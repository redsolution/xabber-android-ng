package com.xabber.domain.entity;

public final class LocalAndDomainpartJid extends AbstractJid implements EntityBareJid {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final DomainBareJid domainBareJid;
    private final Localpart localpart;

    private String unescapedCache;

    LocalAndDomainpartJid(String localpart, String domain) throws XmppStringprepException {
        domainBareJid = new DomainpartJid(domain);
        this.localpart = Localpart.from(localpart);
    }

    LocalAndDomainpartJid(Localpart localpart, Domainpart domain) {
        this.localpart = localpart;
        this.domainBareJid = new DomainpartJid(domain);
    }

    @Override
    public final Localpart getLocalpart() {
        return localpart;
    }

    @Override
    public String toString() {
        if (cache != null) {
            return cache;
        }
        cache = getLocalpart().toString() + '@' + domainBareJid.toString();
        return cache;
    }

    @Override
    public String asUnescapedString() {
        if (unescapedCache != null) {
            return unescapedCache;
        }
        unescapedCache = XmppStringUtils.unescapeLocalpart(getLocalpart().toString()) + '@' + domainBareJid.toString();
        return unescapedCache;
    }

    @Override
    public EntityBareJid asEntityBareJidIfPossible() {
        return this;
    }

    @Override
    public EntityFullJid asEntityFullJidIfPossible() {
        return null;
    }

    @Override
    public DomainFullJid asDomainFullJidIfPossible() {
        return null;
    }

    @Override
    public boolean isParentOf(EntityBareJid bareJid) {
        return domainBareJid.equals(bareJid.getDomain()) && localpart.equals(bareJid.getLocalpart());
    }

    @Override
    public boolean isParentOf(EntityFullJid fullJid) {
        return isParentOf(fullJid.asBareJid());
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
        return domainBareJid;
    }

    @Override
    public Domainpart getDomain() {
        return domainBareJid.getDomain();
    }

    @Override
    public BareJid asBareJid() {
        return this;
    }

    @Override
    public boolean hasNoResource() {
        return true;
    }

    @Override
    public EntityJid asEntityJidIfPossible() {
        return this;
    }

    @Override
    public com.xabber.domain.entity.FullJid asFullJidIfPossible() {
        return null;
    }

    @Override
    public EntityBareJid asEntityBareJid() {
        return this;
    }

    @Override
    public Resourcepart getResourceOrNull() {
        return null;
    }

    @Override
    public Localpart getLocalpartOrNull() {
        return getLocalpart();
    }

    @Override
    public String asEntityBareJidString() {
        return toString();
    }
}
