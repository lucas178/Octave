package xyz.gnarbot.gnar.db.premium;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.rethinkdb.net.Cursor;
import xyz.gnarbot.gnar.Bot;
import xyz.gnarbot.gnar.db.ManagedObject;

import javax.annotation.Nullable;
import java.beans.ConstructorProperties;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PremiumUser extends ManagedObject {
    // The ID of the user that enabled premium for the guild.
    @JsonSerialize
    @JsonDeserialize
    private Double pledgeAmount;

    @ConstructorProperties("id")
    public PremiumUser(String id) {
        super(id, "premiumusers");
    }

    @JsonIgnore
    public PremiumUser setPledgeAmount(Double pledgeAmount) {
        this.pledgeAmount = pledgeAmount;
        return this;
    }

    @JsonIgnore
    public Double getPledgeAmount() {
        return pledgeAmount;
    }

    @JsonIgnore
    @Nullable
    public Cursor<PremiumGuild> getPremiumGuilds() {
        return Bot.getInstance().db().getPremiumGuilds(getId());
    }

    @JsonIgnore
    public List<PremiumGuild> getPremiumGuildsList() {
        Cursor<PremiumGuild> cursor = getPremiumGuilds();
        return cursor == null ? List.of() : cursor.toList();
    }

    @JsonIgnore
    public int getTotalPremiumGuildQuota() {
        if (Bot.getInstance().getConfiguration().getAdmins().contains(Long.parseLong(getId()))) {
            return 99999;
        } else if (pledgeAmount >= 20.0) {  // base: 5 servers + 1 for every extra $3
            int extra = (int) ((pledgeAmount - 20) / 3);
            return 5 + extra;
        } else if (pledgeAmount >= 10.0) {
            return 2;
        } else if (pledgeAmount >= 5) {
            return 1;
        } else {
            return 0;
        }
    }

    @JsonIgnore
    public long getSongSizeQuota() {
        if (Bot.getInstance().getConfiguration().getAdmins().contains(Long.parseLong(getId()))) {
            return Integer.MAX_VALUE;
        } else if (pledgeAmount >= 10) {
            return TimeUnit.MINUTES.toMillis(720);
        } else if (pledgeAmount >= 5) {
            return TimeUnit.MINUTES.toMillis(360);
        } else {
            return Bot.getInstance().getConfiguration().getDurationLimit().toMillis();
        }
    }

    @JsonIgnore
    public int getQueueSizeQuota() {
        if (Bot.getInstance().getConfiguration().getAdmins().contains(Long.parseLong(getId())) || pledgeAmount >= 10) {
            return Integer.MAX_VALUE;
        } else if (pledgeAmount >= 5) {
            return 500;
        } else {
            return Bot.getInstance().getConfiguration().getQueueLimit();
        }
    }

    @JsonIgnore
    public boolean isPremium() {
        return pledgeAmount >= 2;
    }

    @JsonIgnore
    public Integer getRemainingPremiumGuildQuota() {
        Cursor<PremiumGuild> cursor = getPremiumGuilds();

        if (cursor == null) {
            return 0;
            // In case of database error, return 0 to ensure users can't exploit malfunctions.
        }

        List<PremiumGuild> redeemedGuilds = cursor.toList();
        return getTotalPremiumGuildQuota() - redeemedGuilds.size();
    }
}