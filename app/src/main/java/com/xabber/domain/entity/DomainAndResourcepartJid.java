package com.xabber.domain.entity;


/**
 * RFC6122 2.4 allows JIDs with only a domain and resource part.
 * <p>
 * Note that this implementation does not require an cache for the unescaped
 * string, compared to {@link LocalDomainAndResourcepartJid}.
 * </p>
 *
 */
public final class DomainAndResourcepartJid extends AbstractJid implements DomainFullJid {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final DomainBareJid domainBareJid;
    private final Resourcepart resource;

    DomainAndResourcepartJid(String domain, String resource) throws XmppStringprepException {
        this(new DomainpartJid(domain), Resourcepart.from(resource));
    }

    DomainAndResourcepartJid(DomainBareJid domainBareJid, Resourcepart resource) {
        this.domainBareJid = domainBareJid;
        this.resource = resource;
    }

    @Override
    public String toString() {
        if (cache != null) {
            return cache;
        }
        cache = domainBareJid.toString() + '/' + resource;
        return cache;
    }

    @Override
    public DomainBareJid asDomainBareJid() {
        return domainBareJid;
    }

    @Override
    public final boolean hasNoResource() {
        return false;
    }

    @Override
    public EntityBareJid asEntityBareJidIfPossible() {
        return null;
    }

    @Override
    public EntityFullJid asEntityFullJidIfPossible() {
        return null;
    }

    @Override
    public DomainFullJid asDomainFullJidIfPossible() {
        return this;
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
        return false;
    }

    @Override
    public boolean isParentOf(DomainBareJid domainBareJid) {
        return false;
    }

    @Override
    public boolean isParentOf(DomainFullJid domainFullJid) {
        return domainBareJid.equals(domainFullJid.getDomain()) && resource.equals(domainFullJid.getResourcepart());
    }

    @Override
    public Resourcepart getResourcepart() {
        return resource;
    }

    @Override
    public BareJid asBareJid() {
        return asDomainBareJid();
    }

    @Override
    public Domainpart getDomain() {
        return domainBareJid.getDomain();
    }

    @Override
    public String asUnescapedString() {
        return toString();
    }

    @Override
    public EntityJid asEntityJidIfPossible() {
        return null;
    }

    @Override
    public com.xabber.domain.entity.FullJid asFullJidIfPossible() {
        return this;
    }

    @Override
    public Localpart getLocalpartOrNull() {
        return null;
    }
}

