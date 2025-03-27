package com.omnia.migrator;

public class CommuneId {
    private final String Id;

    public CommuneId(String id) {
        Id = id;
    }

    static public CommuneId fromString(String id) {
        return new CommuneId(id);
    }

    public String ToString() {
        return Id;
    }
}
