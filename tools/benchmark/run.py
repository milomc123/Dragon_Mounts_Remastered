#!/usr/bin/env python3
"""Run identical, sequential dedicated-server workloads against a git ref and this checkout."""
import argparse
import csv
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def prepare_server(directory):
    directory.mkdir(parents=True)
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25575\nonline-mode=false\n'
        'level-type=minecraft:flat\nlevel-seed=8675309\ngenerate-structures=false\n'
        'view-distance=6\nsimulation-distance=6\nspawn-protection=0\nmax-players=1\n'
        'max-tick-time=120000\nsync-chunk-writes=true\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='main')
    parser.add_argument('--reuse-first-baseline', type=Path, help='Reuse an identical completed baseline server directory')
    parser.add_argument('--rounds', type=int, default=2)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--gradle-user-home', type=Path, default=Path('/private/tmp/dmr-gradle'))
    parser.add_argument('--accept-eula', action='store_true', help='Accept the Minecraft EULA for these temporary servers')
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error('Local servers require --accept-eula (https://aka.ms/MinecraftEULA).')
    if args.rounds < 1:
        parser.error('--rounds must be positive')
    output = args.output or Path(tempfile.mkdtemp(prefix='dmr-benchmark-'))
    output = output.resolve()
    if output == ROOT or ROOT in output.parents:
        parser.error('--output must be outside the source checkout')
    output.mkdir(parents=True, exist_ok=True)
    baseline = output / 'baseline-source'
    baseline.mkdir()  # Refuse to overwrite an existing checkout or previous experiment.
    archive = subprocess.check_output(['git', 'archive', args.base], cwd=ROOT)
    subprocess.run(['tar', '-x', '-C', str(baseline)], input=archive, check=True)
    shutil.copytree(ROOT / 'tools/benchmark', baseline / 'tools/benchmark')
    marker = '// The harness is deliberately excluded'
    benchmark_config = (ROOT / 'build.gradle').read_text().split(marker, 1)[1]
    with (baseline / 'build.gradle').open('a') as build:
        build.write('\n' + marker + benchmark_config)
    candidate = output / 'performance-source'
    candidate.mkdir()
    head_archive = subprocess.check_output(['git', 'archive', 'HEAD'], cwd=ROOT)
    subprocess.run(['tar', '-x', '-C', str(candidate)], input=head_archive, check=True)
    files = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT).decode().split('\0')
    for name in files:
        if not name or name.startswith('.vscode/'):
            continue
        source_file = ROOT / name
        target_file = candidate / name
        if source_file.is_file():
            target_file.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_file, target_file)
        elif target_file.is_file():
            target_file.unlink()
    if args.reuse_first_baseline:
        previous = args.reuse_first_baseline.resolve()
        previous_metadata = json.loads((previous.parent / 'metadata.json').read_text())
        commit = subprocess.check_output(['git', 'rev-parse', args.base], cwd=ROOT, text=True).strip()
        old_harness = previous.parent / 'baseline-source/tools/benchmark/java/dmr/benchmark/DragonBenchmark.java'
        if previous_metadata['base_commit'] != commit or old_harness.read_bytes() != (ROOT / 'tools/benchmark/java/dmr/benchmark/DragonBenchmark.java').read_bytes():
            raise RuntimeError('Reused baseline must have the same commit and identical Java harness')
    env = os.environ.copy()
    env['GRADLE_USER_HOME'] = str(args.gradle_user_home.resolve())
    metadata = {
        'base_ref': args.base,
        'base_commit': subprocess.check_output(['git', 'rev-parse', args.base], cwd=ROOT, text=True).strip(),
        'performance_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'performance_diff': 'performance.patch',
        'rounds': args.rounds,
        'reused_first_baseline': str(args.reuse_first_baseline.resolve()) if args.reuse_first_baseline else None,
        'java': subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True),
        'method': '100 warmup + 200 measured ticks per scenario; sequential servers; alternating branch order; identical fixed attributes and RNG seeds; 2 GiB heap',
    }
    (output / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    (output / 'performance.patch').write_bytes(subprocess.check_output(['git', 'diff', 'HEAD'], cwd=ROOT))
    results = []
    for round_number in range(1, args.rounds + 1):
        order = ['main', 'performance'] if round_number % 2 else ['performance', 'main']
        for label in order:
            directory = output / f'{label}-{round_number}'
            if label == 'main' and round_number == 1 and args.reuse_first_baseline:
                directory.mkdir()
                shutil.copy2(args.reuse_first_baseline / 'benchmark.csv', directory / 'benchmark.csv')
                print(f'Reusing verified baseline: {args.reuse_first_baseline}', flush=True)
            else:
                prepare_server(directory)
                source = baseline if label == 'main' else candidate
                command = ['bash', 'gradlew', 'runServer', f'-PdragonBenchmark={directory}', '--console=plain']
                print(f'Running {label} round {round_number}: {directory}', flush=True)
                with (output / f'{label}-{round_number}.log').open('w') as log:
                    subprocess.run(command, cwd=source, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
            with (directory / 'benchmark.csv').open() as handle:
                rows = list(csv.DictReader(handle))
            if len(rows) != 9:
                raise RuntimeError(f'{label}: incomplete benchmark ({len(rows)} scenarios)')
            for row in rows:
                if int(row['entity_ticks']) != int(row['samples']) * int(row['dragons']):
                    raise RuntimeError(f'{label}: workload did not tick all dragons: {row}')
                results.append({'branch': label, 'round': round_number, **row})
            with (output / 'results.csv').open('w') as handle:
                writer = csv.DictWriter(handle, fieldnames=list(results[0]), lineterminator='\n')
                writer.writeheader()
                writer.writerows(results)
    print(f'Results: {output / "results.csv"}', flush=True)


if __name__ == '__main__':
    main()
