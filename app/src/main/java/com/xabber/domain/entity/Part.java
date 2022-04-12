package com.xabber.domain.entity;

import java.io.Serializable;

public abstract class Part implements CharSequence, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final String part;
    /**
     * The cache holding the internalized value of this part. This needs to be transient so that the
     * cache is recreated once the data was de-serialized.
     */
    private transient String internalizedCache;

    protected Part(String part) {
        this.part = part;
    }

    protected static void assertNotLongerThan1023BytesOrEmpty(String string) throws XmppStringprepException {
        char[] bytes = string.toCharArray();

        // Better throw XmppStringprepException instead of IllegalArgumentException here, because users don't expect an
        // IAE and it also makes the error handling for users easier.
        if (bytes.length > 1023) {
            throw new XmppStringprepException(string, "Given string is longer then 1023 bytes");
        } else if (bytes.length == 0) {
            throw new XmppStringprepException(string, "Argument can't be the empty string");
        }
    }

    @Override
    public final int length() {
        return part.length();
    }

    @Override
    public final char charAt(int index) {
        return part.charAt(index);
    }

    @Override
    public final CharSequence subSequence(int start, int end) {
        return part.subSequence(start, end);
    }

    @Override
    public final String toString() {
        return part;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return part.equals(other.toString());
    }

    @Override
    public final int hashCode() {
        return part.hashCode();
    }

    /**
     * Returns the canonical String representation of this Part. See {@link String#intern} for details.
     *
     * @return the canonical String representation.
     */
    public final String intern() {
        if (internalizedCache == null) {
            internalizedCache = toString().intern();
        }
        return internalizedCache;
    }
}
