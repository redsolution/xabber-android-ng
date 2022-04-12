package com.xabber.domain.entity;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;



import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccountJid implements Comparable<com.xabber.domain.entity.AccountJid>, Parcelable, Serializable {

    private static final Map<com.xabber.domain.entity.FullJid, com.xabber.domain.entity.AccountJid> instances = new ConcurrentHashMap<>();
    public static final Creator<com.xabber.domain.entity.AccountJid> CREATOR = new Creator<com.xabber.domain.entity.AccountJid>() {
        @Override
        public com.xabber.domain.entity.AccountJid createFromParcel(Parcel parcel) {
            try {
                return com.xabber.domain.entity.AccountJid.from(parcel.readString());
            } catch (XmppStringprepException e) {
                Log.e("qwe", e.getCausingString());
                return null;
            }
        }

        @Override
        public com.xabber.domain.entity.AccountJid[] newArray(int size) {
            return new com.xabber.domain.entity.AccountJid[size];
        }
    };
    private final @NonNull
    com.xabber.domain.entity.FullJid fullJid;
    private int order = 0;

    private AccountJid(@NonNull com.xabber.domain.entity.FullJid fullJid) {
        this.fullJid = fullJid;
    }

    public static com.xabber.domain.entity.AccountJid from(Localpart localpart, DomainBareJid domainBareJid, Resourcepart resource) {
        return getAccountJid(JidCreate.fullFrom(localpart, domainBareJid, resource));
    }

    public static com.xabber.domain.entity.AccountJid from(@NonNull String string) throws XmppStringprepException {
        return getAccountJid(JidCreate.fullFrom(string));
    }

    private static com.xabber.domain.entity.AccountJid getAccountJid(@NonNull com.xabber.domain.entity.FullJid fullJid) {
        com.xabber.domain.entity.AccountJid accountJid = instances.get(fullJid);
        if (accountJid != null) {
            return accountJid;
        } else {
            com.xabber.domain.entity.AccountJid newAccountJid = new com.xabber.domain.entity.AccountJid(fullJid);
            instances.put(fullJid, newAccountJid);
            return newAccountJid;
        }
    }

    public @NonNull
    com.xabber.domain.entity.FullJid getFullJid() {
        return fullJid;
    }

    public BareJid getBareJid() {
        return fullJid.asBareJid();
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int compareTo(@NonNull com.xabber.domain.entity.AccountJid another) {
        return this.order - another.order;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof com.xabber.domain.entity.AccountJid) {
            return getFullJid().equals(((com.xabber.domain.entity.AccountJid) o).getFullJid());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return getFullJid().hashCode();
    }

    @NotNull
    @Override
    public String toString() {
        return getFullJid().toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(fullJid.toString());
    }

}
