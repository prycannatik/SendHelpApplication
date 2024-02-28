package com.example.sendhelpapplication;

import android.provider.BaseColumns;

public final class TerminContract {
    // Verhindern, dass jemand eine Instanz der Vertragsklasse erstellt.
    private TerminContract() {}

    // Innere Klasse, die die Tabelle definiert
    public static class TerminEntry implements BaseColumns {
        public static final String TABLE_NAME = "termin";
        public static final String COLUMN_NAME_NAME = "name";
        public static final String COLUMN_NAME_AHV_NUMMER = "ahv_nummer";
        public static final String COLUMN_NAME_TERMIN_TYP = "termin_typ";
        public static final String COLUMN_NAME_DETAILS = "details";
        public static final String COLUMN_NAME_DATUM = "datum";
    }
}

