package ox.lang;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Created by arrdem on 9/19/15.
 *
 * A Keyword must have a non-null, legal getName.
 * A Keyword may have a namespace, but it may be null.
 */
public class Keyword implements INamed, IMeta {
    private final String name;
    private final String namespace;
    private final Map meta;

    private Keyword(String n, String ns, Map meta) {
        this.name = n;
        this.namespace = ns;
        this.meta = meta;
    }

    public static final class Builder {
        private String name;
        private String namespace;
        private Map meta;
        private Keyword result;

        public Builder() {
            this.namespace = null;
            this.name = null;
            this.meta = ImmutableMap.of();
            this.result = null;
        }

        public Keyword build() {
            if(result != null) {
                return result;
            } else {
                if (this.name == null) {
                    throw new RuntimeException(
                            "Cannot build a keyword with a null name!");
                }
                result = new Keyword(name, namespace, meta);
                return result;
            }
        }

        public Builder setName(String name) {
            if(Util.isValidName(name)) {
                this.name = name;
                return this;
            } else {
                throw new RuntimeException(
                        String.format("Illegal name '%s'!", name));
            }
        }

        public Builder setNamespace(String ns) {
            if(Util.isValidNamespace(ns)) {
                this.namespace = ns;
                return this;
            } else {
                throw new RuntimeException(
                        String.format("Illegal namespace '%s'!", ns));
            }
        }
    }

    /* INamed
     */
    @Override
    public String getName() {
        return name;
    }

    /* INamed
     */
    @Override
    public String getNamespace() {
        return namespace;
    }

    /* IMeta
     */
    @Override
    public Map getMeta() {
        return meta;
    }

    /* IMeta
     */
    @Override
    public Object withMeta(Map meta) {
        return new Keyword(name, namespace, meta);
    }

    /* Object
     */
    public String toString() {
        if(namespace != null) {
            return String.format(":%s/%s", namespace, name);
        } else {
            return String.format(":%s", name);
        }
    }

    /* Object
     */
    public boolean equals(Object other) {
        if(other instanceof Keyword) {
            Keyword otherK = (Keyword) other;
            return ((this.name.equals(otherK.name)) &&
                    (this.namespace.equals(otherK.namespace)));
        } else {
            return false;
        }
    }
}
