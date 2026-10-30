# Test Summary
All tests passed

|Group             | ✅ Succeeded | ❌ Failed | ⚠️ Skipped |
| ---------------- |:-----------:|:--------:|:----------:|
|Dragon Breeds     |      8      |    0     |     0      |
|Network           |      1      |    0     |     0      |
|Capabilities      |      6      |    0     |     0      |
|Breeding          |      2      |    0     |     0      |
|Dragon Armors     |      5      |    0     |     0      |
|Dragons           |     15      |    0     |     0      |
|Loot Tables       |      2      |    0     |     0      |
|DataPack          |      3      |    0     |     0      |
|Dragon Whistles   |     12      |    0     |     0      |
|Dragon Eggs       |     11      |    0     |     0      |
|DragonNavigation  |     10      |    0     |     0      |

## Test Results

|Test group        |Test Id                                | Test Result |Status message   |Test description  |
| ---------------- | ------------------------------------- |:-----------:| --------------- | ---------------- |
|Dragon Breeds     |verifyHybridProperties                 |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |getHybridByParents                     |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |hasHybrids                             |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |checkBreedExists                       |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |getBreedById                           |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |hasFirstBreed                          |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |hasDefaultBreed                        |      ✅      |GameTest passed  |                  |
|Dragon Breeds     |hasBreeds                              |      ✅      |GameTest passed  |                  |
|Network           |testDragonStatePacket                  |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |hatchCountIncremented                  |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |correctDragonTypeHatches               |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |correctDragonEggType                   |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |eggHatches                             |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |eggTick                                |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |eggBlockEntity                         |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |hatchEgg                               |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |dropCorrectEgg                         |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |dropEgg                                |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |breakEgg                               |      ✅      |GameTest passed  |                  |
|Dragon Eggs       |placeEgg                               |      ✅      |GameTest passed  |                  |
|Breeding          |testGenerateCustomName                 |      ✅      |GameTest passed  |                  |
|Breeding          |testHabitatBreedOutcomes               |      ✅      |GameTest passed  |                  |
|Dragons           |dismountDragon                         |      ✅      |GameTest passed  |                  |
|Dragons           |rideDragon                             |      ✅      |GameTest passed  |                  |
|Dragons           |dropArmorOnDeath                       |      ✅      |GameTest passed  |                  |
|Dragons           |dropSaddleOnDeath                      |      ✅      |GameTest passed  |                  |
|Dragons           |dropChestContentsOnDeath               |      ✅      |GameTest passed  |                  |
|Dragons           |dontDropWhenSelected                   |      ✅      |GameTest passed  |                  |
|Dragons           |giveChestToDragon                      |      ✅      |GameTest passed  |                  |
|Dragons           |armorDragon                            |      ✅      |GameTest passed  |                  |
|Dragons           |saddleDragon                           |      ✅      |GameTest passed  |                  |
|Dragons           |willNotAttackTamed                     |      ✅      |GameTest passed  |                  |
|Dragons           |willNotAttackOwner                     |      ✅      |GameTest passed  |                  |
|Dragons           |willAttackOwnerTarget                  |      ✅      |GameTest passed  |                  |
|Dragons           |willLookAtPlayer                       |      ✅      |GameTest passed  |                  |
|Dragons           |tamedFor                               |      ✅      |GameTest passed  |                  |
|Dragons           |tameDragon                             |      ✅      |GameTest passed  |                  |
|Capabilities      |createDragonEntity                     |      ✅      |GameTest passed  |                  |
|Capabilities      |setDragonSetsCorrectSlot               |      ✅      |GameTest passed  |                  |
|Capabilities      |setDragon                              |      ✅      |GameTest passed  |                  |
|Capabilities      |setPlayer                              |      ✅      |GameTest passed  |                  |
|Capabilities      |hasPlayerBeenSet                       |      ✅      |GameTest passed  |                  |
|Capabilities      |getCapability                          |      ✅      |GameTest passed  |                  |
|Dragon Armors     |checkArmorExists                       |      ✅      |GameTest passed  |                  |
|Dragon Armors     |getArmorById                           |      ✅      |GameTest passed  |                  |
|Dragon Armors     |hasFirstArmor                          |      ✅      |GameTest passed  |                  |
|Dragon Armors     |hasDefaultArmor                        |      ✅      |GameTest passed  |                  |
|Dragon Armors     |hasArmors                              |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |bindMultipleDragons                    |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |tamedDragonsDoNotDespawn               |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |callAcrossDimensions                   |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |dragonFollowsWhenCalled                |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |summonUpdatesLastCall                  |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |callBoundDragon                        |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |locateBoundDragon                      |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |cannotCallWithoutDragon                |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |cannotCallWithoutWhistle               |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |bindDragonToWhistle                    |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |getWhistleIndex                        |      ✅      |GameTest passed  |                  |
|Dragon Whistles   |findWhistleItem                        |      ✅      |GameTest passed  |                  |
|DragonNavigation  |exhaustedBudgetPreservesWalkTarget     |      ✅      |GameTest passed  |                  |
|DragonNavigation  |failedRouteBacksOffAndRetries          |      ✅      |GameTest passed  |                  |
|DragonNavigation  |groundRoutePreservesDetour             |      ✅      |GameTest passed  |                  |
|DragonNavigation  |elevatedTargetTriggersTakeoff          |      ✅      |GameTest passed  |                  |
|DragonNavigation  |groundRouteMakesProgressWithoutFlying  |      ✅      |GameTest passed  |                  |
|DragonNavigation  |hatchlingNeverPlansFlight              |      ✅      |GameTest passed  |                  |
|DragonNavigation  |sitCancelsMovementAndCombat            |      ✅      |GameTest passed  |                  |
|DragonNavigation  |walkingEvaluatorReleasesContext        |      ✅      |GameTest passed  |                  |
|DragonNavigation  |entityAndBlockRequestsAgree            |      ✅      |GameTest passed  |                  |
|DragonNavigation  |immediatePathRequest                   |      ✅      |GameTest passed  |                  |
|DataPack          |testRegistryUpdates                    |      ✅      |GameTest passed  |                  |
|DataPack          |testDragonArmorRegistry                |      ✅      |GameTest passed  |                  |
|DataPack          |testDragonBreedRegistry                |      ✅      |GameTest passed  |                  |
|Loot Tables       |testLootTableAddPool                   |      ✅      |GameTest passed  |                  |
|Loot Tables       |testInjectEggLoot                      |      ✅      |GameTest passed  |                  |
