package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;

public class HouseSignBlock extends Block implements EntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
        
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final EnumProperty<SignType> SIGN_TYPE = EnumProperty.create("sign_type", SignType.class);
    public static final EnumProperty<HolderType> HOLDER_TYPE = EnumProperty.create("holder_type", HolderType.class);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape base = Block.box(0, 0, 0, 16, 16, 16);
        VoxelShape sign = switch (facing) {
            case NORTH -> Block.box(16, 0, 0, 32, 16, 16);
            case SOUTH -> Block.box(-16, 0, 0, 0, 16, 16);
            case WEST -> Block.box(0, 0, -16, 16, 16, 0);
            case EAST -> Block.box(0, 0, 16, 16, 16, 32);
            default -> Block.box(16, 0, 0, 32, 16, 16);
        };
        return Shapes.or(base, sign);
    }

    public enum HolderType implements StringRepresentable {
        WOOD_1("wood_1"),
        WOOD_2("wood_2"),
        METAL_1("metal_1"),
        METAL_2("metal_2"),
        METAL_3("metal_3"),
        METAL_4("metal_4");

        private final String name;

        HolderType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SignType implements StringRepresentable {
      
        BAKER("baker", SignShape.OVAL),
        HEALER("healer", SignShape.RECTANGLE),
        INN("inn", SignShape.OVAL),
 BARD("bard", SignShape.OVAL),
  TAVERN("tavern", SignShape.OVAL),
  PROVISIONER("provisioner", SignShape.RECTANGLE),
        TAILOR("tailor", SignShape.RECTANGLE),
      MAGE("mage", SignShape.RECTANGLE),
  SHIPWRIGHT("shipwright", SignShape.OVAL),
   FLETCHER("fletcher", SignShape.RECTANGLE),
  REAGENTS("reagents", SignShape.OVAL),
  BOWYER("bowyer", SignShape.OVAL),
   TINKER("tinker", SignShape.RECTANGLE),
   CARPENTER("carpenter", SignShape.RECTANGLE),
   STABLES("stables", SignShape.RECTANGLE),
   ARMORER("armorer", SignShape.RECTANGLE),
   BLACKSMITH("blacksmith", SignShape.RECTANGLE),
 BLANK("blank", SignShape.RECTANGLE),
 BUTCHER("butcher", SignShape.RECTANGLE),
  CUSTOMS("customs", SignShape.OVAL),

 BARBER("barber", SignShape.RECTANGLE),

 JEWELER("jeweler", SignShape.RECTANGLE),

  ARTIST("artist", SignShape.OVAL),
    DEFAULT("default", SignShape.RECTANGLE),

        LIBRARY("library", SignShape.OVAL),
        THEATRE("theatre", SignShape.OVAL),
        BEEKEEPER("beekeeper", SignShape.OVAL),
        MERCHANT("merchant", SignShape.RECTANGLE),
        BANK("bank", SignShape.OVAL),
     GUILD_ARCHERS("guild_archers", SignShape.RECTANGLE),
GUILD_ARMAMENTS("guild_armaments", SignShape.RECTANGLE),
GUILD_ARMORERS("guild_armorers", SignShape.RECTANGLE),
GUILD_ASSASSINS("guild_assassins", SignShape.RECTANGLE),
GUILD_BARDIC("guild_bardic", SignShape.RECTANGLE),
GUILD_BARTERS("guild_barters", SignShape.RECTANGLE),
GUILD_BLACKSMITHS("guild_blacksmiths", SignShape.RECTANGLE),
GUILD_CALVARY("guild_calvary", SignShape.RECTANGLE),
GUILD_COOKS("guild_cooks", SignShape.RECTANGLE),
GUILD_FIGHTERS("guild_fighters", SignShape.RECTANGLE),
GUILD_FISHERMEN("guild_fishermen", SignShape.RECTANGLE),
GUILD_HEALERS("guild_healers", SignShape.RECTANGLE),
GUILD_ILLUSIONIST("guild_illusionist", SignShape.RECTANGLE),
GUILD_MAGES("guild_mages", SignShape.RECTANGLE),
GUILD_MINERS("guild_miners", SignShape.RECTANGLE),
GUILD_PROVISIONERS("guild_provisioners", SignShape.RECTANGLE),
GUILD_ROGUES("guild_rogues", SignShape.RECTANGLE),
GUILD_SAILORS("guild_sailors", SignShape.RECTANGLE),
GUILD_SEAMENS("guild_seamens", SignShape.RECTANGLE),
GUILD_SHIPWRIGHT("guild_shipwright", SignShape.RECTANGLE),
GUILD_SORCERERS("guild_sorcerers", SignShape.RECTANGLE),
GUILD_TAILORS("guild_tailors", SignShape.RECTANGLE),
GUILD_THIEVES("guild_thieves", SignShape.RECTANGLE),
GUILD_TINKERS("guild_tinkers", SignShape.RECTANGLE),
GUILD_TRADERS("guild_traders", SignShape.RECTANGLE),
GUILD_WARRIORS("guild_warriors", SignShape.RECTANGLE),
GUILD_WEAPONS("guild_weapons", SignShape.RECTANGLE),
HOBANGER("hobanger", SignShape.OVAL);

        private final String name;
        private final SignShape shape;

        public ResourceLocation icon() {
            return ResourceLocation.fromNamespaceAndPath(
                "britannia_mod",
                "textures/screens/sign_icons/" + getSerializedName() + ".png"
            );
        }


        SignType(String name, SignShape shape) {
            this.name = name;
            this.shape = shape;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        public SignShape getShape() {
            return shape;
        }

        public enum SignShape {
            RECTANGLE,
            OVAL
        }
    }

    public HouseSignBlock() {
        super(BlockBehaviour.Properties
            .of()
            .noOcclusion()
            .strength(3.0f, 3.0f)
        );

        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SIGN_TYPE, SignType.DEFAULT)
                .setValue(HOLDER_TYPE, HolderType.WOOD_1)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIGN_TYPE, HOLDER_TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HouseSignBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        LOGGER.info("Interacted with sign");

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        BlockEntity below = level.getBlockEntity(pos.below());
        if (!(below instanceof HouseLotBlockEntity lot)) {
            player.sendSystemMessage(Component.literal("Could not find the house controller."));
            return InteractionResult.FAIL;
        }

        LOGGER.info("EntityBlockFound");

        if (!lot.getOwner().equals(player.getName().getString())) {
            player.sendSystemMessage(Component.literal("You are not the owner of this house."));
            return InteractionResult.FAIL;
        }

        LOGGER.info("Is owner");

        HouseManagementScreenPayload.send(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }
}
