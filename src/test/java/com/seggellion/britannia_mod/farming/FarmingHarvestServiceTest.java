package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.skill.SkillManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingHarvestServiceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void all74SpeciesShareExactThresholdProbabilityAndStrictSingleRollBoundary() {
        List<FarmingSkillRequirementResolver.ResolvedRequirement> all=new ArrayList<>();
        for(var crop:CropRegistry.all()) all.add(new FarmingSkillRequirementResolver.ResolvedRequirement(crop.id(),crop));
        for(var flower:FlowerRegistry.initial().definitions().values()) all.add(new FarmingSkillRequirementResolver.ResolvedRequirement(flower.id().toString(),flower));
        assertEquals(74,all.size());
        for(var requirement:all) for(float surplus:new float[]{0,.5f,1,10,20,30}) {
            float skill=requirement.minimumFarmingSkill()+surplus;
            var eligibility=FarmingCultivationGate.evaluateResolved(requirement,FarmingCultivationGate.Subject.loadedPlayer(skill));
            double chance=Math.min(.95,.75+.01*surplus);
            assertEquals(chance,FarmingHarvestService.successChance(skill,requirement.minimumFarmingSkill()),1e-9);
            var calls=new AtomicInteger();
            assertEquals(FarmingHarvestService.Outcome.SUCCESS,FarmingHarvestService.roll(eligibility,()->{calls.incrementAndGet();return Math.nextDown(chance);}));
            assertEquals(1,calls.get());
            assertEquals(FarmingHarvestService.Outcome.FAILURE,FarmingHarvestService.roll(eligibility,()->chance));
            var below=FarmingCultivationGate.evaluateResolved(requirement,FarmingCultivationGate.Subject.loadedPlayer(requirement.minimumFarmingSkill()-.01f));
            assertEquals(FarmingHarvestService.Outcome.REFUSED,FarmingHarvestService.roll(below,()->{throw new AssertionError("below threshold rolled");}));
        }
    }
    @Test void unavailableNonfiniteAndAutomationRefuseWithoutRngWhileCreativeAndAdminAreFree() {
        var grape=CropRegistry.byId("grapes").orElseThrow();
        var requirement=new FarmingSkillRequirementResolver.ResolvedRequirement(grape.id(),grape);
        for(var state:SkillManager.SkillDataState.values()) if(state!=SkillManager.SkillDataState.AVAILABLE) {
            var subject=new FarmingCultivationGate.Subject(FarmingCultivationGate.ActorType.PLAYER,false,0,state,100);
            assertNoRoll(requirement,subject,FarmingHarvestService.Outcome.REFUSED);
        }
        for(float value:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY})
            assertNoRoll(requirement,FarmingCultivationGate.Subject.loadedPlayer(value),FarmingHarvestService.Outcome.REFUSED);
        for(var actor:new FarmingCultivationGate.ActorType[]{FarmingCultivationGate.ActorType.AUTOMATION,FarmingCultivationGate.ActorType.NON_PLAYER})
            assertNoRoll(requirement,new FarmingCultivationGate.Subject(actor,true,4,SkillManager.SkillDataState.AVAILABLE,100),FarmingHarvestService.Outcome.REFUSED);
        assertNoRoll(requirement,new FarmingCultivationGate.Subject(FarmingCultivationGate.ActorType.PLAYER,true,0,SkillManager.SkillDataState.NOT_LOADED,Float.NaN),FarmingHarvestService.Outcome.FREE_SUCCESS);
        assertNoRoll(requirement,new FarmingCultivationGate.Subject(FarmingCultivationGate.ActorType.PLAYER,false,2,SkillManager.SkillDataState.NOT_LOADED,Float.NaN),FarmingHarvestService.Outcome.FREE_SUCCESS);
    }
    private static void assertNoRoll(FarmingSkillRequirementResolver.ResolvedRequirement requirement,FarmingCultivationGate.Subject subject,FarmingHarvestService.Outcome expected) {
        assertEquals(expected,FarmingHarvestService.roll(FarmingCultivationGate.evaluateResolved(requirement,subject),()->{throw new AssertionError("unexpected RNG");}));
    }
}
