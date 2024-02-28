package com.example.sendhelpapplication;

public class Termin {
    private long id; // Für Datenbank-ID
    public String name;
    public String ahvNummer;
    public String terminTyp;
    public String details;
    public String terminDatum;
    private boolean isSynced; // Für den Synchronisationsstatus

    // Default-Konstruktor
    public Termin() {
    }

    // Konstruktor mit allen Parametern
    public Termin(String name, String ahvNummer, String terminTyp, String details, String terminDatum) {
        this.name = name;
        this.ahvNummer = ahvNummer;
        this.terminTyp = terminTyp;
        this.details = details;
        this.terminDatum = terminDatum;
        this.isSynced = false; // Standardmäßig nicht synchronisiert
    }

    // Setter- und Getter-Methoden
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getAhvNummer() { return ahvNummer; }

    public String getTerminTyp() { return terminTyp; }

    public String getDetails() { return details;}

    public String getDatum() { return terminDatum; }
}