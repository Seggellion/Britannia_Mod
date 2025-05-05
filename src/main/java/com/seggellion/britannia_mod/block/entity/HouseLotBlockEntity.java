package com.seggellion.britannia_mod.block.entity;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.structure.HouseSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.Packet;

import org.jetbrains.annotations.Nullable;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HouseLotBlockEntity extends BlockEntity {
    private String owner;
    private HouseSize houseSize;
    private UUID houseUuid;
    private boolean forSale;
    private int price;
    private List<String> accessList;
    private Instant placedAt;
    private String houseType;
    private String regionName;
    private String houseName;
    

    public HouseLotBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.HOUSE_LOT.get(), pos, state);
        this.owner = "";
        this.houseSize = HouseSize.SMALL;
        this.houseUuid = UUID.randomUUID();
        this.forSale = false;
        this.price = 0;
        this.accessList = new ArrayList<>();
        this.placedAt = Instant.now();
        this.houseType = "";   
        this.regionName = "";   
        this.houseName = "";   
    }

    // ----------------------------------
    // Getters & Setters
    // ----------------------------------
  public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
        setChanged();
    }

    // New method: setOwnerUsername is equivalent to setOwner
    public void setOwnerUsername(String ownerUsername) {
        setOwner(ownerUsername);
    }

    public HouseSize getHouseSize() {
        return houseSize;
    }
    public void setHouseSize(HouseSize houseSize) {
        this.houseSize = houseSize;
        setChanged();
    }

    public UUID getHouseUuid() {

        return houseUuid;
    }
    public void setHouseUuid(UUID houseUuid) {
        this.houseUuid = houseUuid;
        setChanged();
    }

    public boolean isForSale() {
        return forSale;
    }
    public void setForSale(boolean forSale) {
        this.forSale = forSale;
        setChanged();
    }

    public int getPrice() {
        return price;
    }
    public void setPrice(int price) {
        this.price = price;
        setChanged();
    }

    public List<String> getAccessList() {
        return accessList;
    }
    public void setAccessList(List<String> accessList) {
        this.accessList = accessList;
        setChanged();
    }

    public Instant getPlacedAt() {
        return placedAt;
    }
    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
        setChanged();
    }

    // New getter and setter for houseType
    public String getHouseType() {
        return houseType;
    }
    public void setHouseType(String houseType) {
        this.houseType = houseType;
        setChanged();
    }

        // New getter and setter for houseName
    public String getHouseName() {
        return houseName;
    }
    public void setHouseName(String houseName) {
        this.houseName = houseName;
        setChanged();
    }

    // New getter and setter for regionName
    public String getRegionName() {
        return regionName;
    }
    public void setRegionName(String regionName) {
        this.regionName = regionName;
        setChanged();
    }



    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        // If the superclass does not have this method, omit calling super.
        if (tag.contains("Owner")) {
            this.owner = tag.getString("Owner");
        }
        if (tag.contains("HouseSize")) {
            this.houseSize = HouseSize.valueOf(tag.getString("HouseSize"));
        }
        if (tag.contains("HouseUUID")) {
            this.houseUuid = UUID.fromString(tag.getString("HouseUUID"));
        }
        if (tag.contains("HouseType")) {
                this.houseType = tag.getString("HouseType");
        }
        if (tag.contains("HouseName")) {
                this.houseName = tag.getString("HouseName");
        }
        this.forSale = tag.getBoolean("ForSale");
        this.price = tag.getInt("Price");

        if (tag.contains("AccessList", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("AccessList", Tag.TAG_STRING);
            this.accessList.clear();
            for (int i = 0; i < listTag.size(); i++) {
                this.accessList.add(listTag.getString(i));
            }
        }
        if (tag.contains("PlacedAt")) {
            this.placedAt = Instant.parse(tag.getString("PlacedAt"));
        }
    }


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        // If the superclass does not have this method, omit calling super.
        if (tag.contains("Owner")) {
            this.owner = tag.getString("Owner");
        }
        if (tag.contains("HouseSize")) {
            this.houseSize = HouseSize.valueOf(tag.getString("HouseSize"));
        }
        if (tag.contains("HouseUUID")) {
            this.houseUuid = UUID.fromString(tag.getString("HouseUUID"));
        }
        this.forSale = tag.getBoolean("ForSale");
        this.price = tag.getInt("Price");

        if (tag.contains("HouseType")) {
                this.houseType = tag.getString("HouseType");
        }

        if (tag.contains("HouseName")) {
                this.houseName = tag.getString("HouseName");
        }

        if (tag.contains("AccessList", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("AccessList", Tag.TAG_STRING);
            this.accessList.clear();
            for (int i = 0; i < listTag.size(); i++) {
                this.accessList.add(listTag.getString(i));
            }
        }
        if (tag.contains("PlacedAt")) {
            this.placedAt = Instant.parse(tag.getString("PlacedAt"));
        }
    }


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        // If calling super.saveAdditional(tag, provider) is not possible, omit it.
        tag.putString("Owner", this.owner);
        tag.putString("HouseSize", this.houseSize.name());
        tag.putString("HouseType", this.houseType);
        tag.putString("HouseName", this.houseName);
        tag.putString("HouseUUID", this.houseUuid.toString());
        tag.putBoolean("ForSale", this.forSale);
        tag.putInt("Price", this.price);
        

        ListTag listTag = new ListTag();
        for (String user : this.accessList) {
            listTag.add(StringTag.valueOf(user));
        }
        tag.put("AccessList", listTag);
        tag.putString("PlacedAt", this.placedAt.toString());
    }


@Override
public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
    CompoundTag tag = new CompoundTag();
    this.saveAdditional(tag, provider);
    return tag;
}


@Nullable
@Override
public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}



}
