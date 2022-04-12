package com.xabber.domain.entity;

public final class LocalDomainAndResourcepartJid extends AbstractJid implements EntityFullJid {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final EntityBareJid bareJid;
    private final Resourcepart resource;

    private String unescapedCache;

    LocalDomainAndResourcepartJid(String localpart, String domain, String resource) throws XmppStringprepException {
        this(new LocalAndDomainpartJid(localpart, domain), Resourcepart.from(resource));
    }

    LocalDomainAndResourcepartJid(EntityBareJid bareJid, Resourcepart resource) {
        this.bareJid = bareJid;
        this.resource = resource;
    }

    @Override
    public String toString() {
        if (cache != null) {
            return cache;
        }
        cache = bareJid.toString() + '/' + resource;
        return cache;
    }

    @Override
    public String asUnescapedString() {
        if (unescapedCache != null) {
            return unescapedCache;
        }
        unescapedCache = bareJid.asUnescapedString() + '/' + resource;
        return unescapedCache;
    }

    @Override
    public EntityBareJid asEntityBareJid() {
        return bareJid;
    }

    @Override
    public String asEntityBareJidString() {
        return asEntityBareJid().toString();
    }

    @Override
    public final boolean hasNoResource() {
        return false;
    }

    @Override
    public EntityBareJid asEntityBareJidIfPossible() {
        return asEntityBareJid();
    }

    @Override
    public EntityFullJid asEntityFullJidIfPossible() {
        return this;
    }

    @Override
    public DomainFullJid asDomainFullJidIfPossible() {
        return null;
    }

    @Override
    public Localpart getLocalpartOrNull() {
        return bareJid.getLocalpart();
    }

    @Override
    public Resourcepart getResourceOrNull() {
        return getResourcepart();
    }

    @Override
    public boolean isParentOf(EntityBareJid bareJid) {
        return false;
    }

    @Override
    public boolean isParentOf(EntityFullJid fullJid) {
        return this.equals(fullJid);
    }

    @Override
    public boolean isParentOf(DomainBareJid domainBareJid) {
        return false;
    }

    @Override
    public boolean isParentOf(DomainFullJid domainFullJid) {
        return false;
    }

    @Override
    public DomainBareJid asDomainBareJid() {
        return bareJid.asDomainBareJid();
    }

    @Override
    public final Resourcepart getResourcepart() {
        return resource;
    }

    @Override
    public BareJid asBareJid() {
        return asEntityBareJid();
    }

    @Override
    public Domainpart getDomain() {
        return bareJid.getDomain();
    }

    @Override
    public Localpart getLocalpart() {
        return bareJid.getLocalpart();
    }

    @Override
    public EntityJid asEntityJidIfPossible() {
        return this;
    }

    @Override
    public FullJid asFullJidIfPossible() {
        return this;
    }
}

