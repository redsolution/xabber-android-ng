package com.xabber.domain.entity;


public abstract class AbstractJid implements Jid {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Cache for the String representation of this JID.
     */
    protected String cache;
    /**
     * The cache holding the internalized value of this part. This needs to be transient so that the
     * cache is recreated once the data was de-serialized.
     */
    private transient String internalizedCache;

    @Override
    public final boolean isEntityJid() {
        return isEntityBareJid() || isEntityFullJid();
    }

    @Override
    public final boolean isEntityBareJid() {
        return this instanceof EntityBareJid;
    }

    @Override
    public final boolean isEntityFullJid() {
        return this instanceof EntityFullJid;
    }

    @Override
    public final boolean isDomainBareJid() {
        return this instanceof DomainBareJid;
    }

    @Override
    public final boolean isDomainFullJid() {
        return this instanceof DomainFullJid;
    }

    @Override
    public abstract boolean hasNoResource();

    @Override
    public final boolean hasResource() {
        return this instanceof com.xabber.domain.entity.FullJid;
    }

    @Override
    public final boolean hasLocalpart() {
        return this instanceof EntityJid;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends Jid> T downcast() {
        return (T) this;
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public final EntityBareJid asEntityBareJidOrThrow() {
        EntityBareJid entityBareJid = asEntityBareJidIfPossible();
        if (entityBareJid == null) throwIse("can not be converted to EntityBareJid");
        return entityBareJid;
    }

    @Override
    public EntityFullJid asEntityFullJidOrThrow() {
        EntityFullJid entityFullJid = asEntityFullJidIfPossible();
        if (entityFullJid == null) throwIse("can not be converted to EntityFullJid");
        return entityFullJid;
    }

    @Override
    public EntityJid asEntityJidOrThrow() {
        EntityJid entityJid = asEntityJidIfPossible();
        if (entityJid == null) throwIse("can not be converted to EntityJid");
        return entityJid;
    }

    @Override
    public EntityFullJid asFullJidOrThrow() {
        EntityFullJid entityFullJid = asEntityFullJidIfPossible();
        if (entityFullJid == null) throwIse("can not be converted to EntityBareJid");
        return entityFullJid;
    }

    @Override
    public DomainFullJid asDomainFullJidOrThrow() {
        DomainFullJid domainFullJid = asDomainFullJidIfPossible();
        if (domainFullJid == null) throwIse("can not be converted to DomainFullJid");
        return domainFullJid;
    }

    @Override
    public abstract Resourcepart getResourceOrNull();

    @Override
    public final Resourcepart getResourceOrEmpty() {
        Resourcepart resourcepart = getResourceOrNull();
        if (resourcepart == null) return Resourcepart.EMPTY;
        return resourcepart;
    }

    @Override
    public final Resourcepart getResourceOrThrow() {
        Resourcepart resourcepart = getResourceOrNull();
        if (resourcepart == null) throwIse("has no resourcepart");
        return resourcepart;
    }

    @Override
    public abstract Localpart getLocalpartOrNull();

    @Override
    public final Localpart getLocalpartOrThrow() {
        Localpart localpart = getLocalpartOrNull();
        if (localpart == null) throwIse("has no localpart");
        return localpart;
    }

    @Override
    public final boolean isParentOf(Jid jid) {
        EntityFullJid fullJid = jid.asEntityFullJidIfPossible();
        if (fullJid != null) {
            return isParentOf(fullJid);
        }
        EntityBareJid bareJid = jid.asEntityBareJidIfPossible();
        if (bareJid != null) {
            return isParentOf(bareJid);
        }
        DomainFullJid domainFullJid = jid.asDomainFullJidIfPossible();
        if (domainFullJid != null) {
            return isParentOf(domainFullJid);
        }

        return isParentOf(jid.asDomainBareJid());
    }

    @Override
    public final int hashCode() {
        return toString().hashCode();
    }

    @Override
    public final boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if (other instanceof CharSequence) {
            return equals((CharSequence) other);
        }
        return false;
    }

    @Override
    public final boolean equals(CharSequence charSequence) {
        if (charSequence == null) {
            return false;
        }
        return equals(charSequence.toString());
    }

    @Override
    public final boolean equals(String string) {
        return toString().equals(string);
    }

    @Override
    public final int compareTo(Jid  other) {
        String otherString = other.toString();
        String myString = toString();
        return myString.compareTo(otherString);
    }

    @Override
    public final String intern() {
        if (internalizedCache == null) {
            cache = internalizedCache = toString().intern();
        }
        return internalizedCache;
    }

    private void throwIse(String message) {
        String exceptionMessage = "The JID '" + this + "' " + message;
        throw new IllegalStateException(exceptionMessage);
    }
}

