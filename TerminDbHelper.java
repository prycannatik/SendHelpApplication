package com.example.sendhelpapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TerminDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 2; // This has incremented
    public static final String DATABASE_NAME = "Termin.db";

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TerminContract.TerminEntry.TABLE_NAME + " (" +
                    TerminContract.TerminEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    TerminContract.TerminEntry.COLUMN_NAME_NAME + " TEXT," +
                    TerminContract.TerminEntry.COLUMN_NAME_AHV_NUMMER + " TEXT," +
                    TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP + " TEXT," +
                    TerminContract.TerminEntry.COLUMN_NAME_DETAILS + " TEXT," +
                    TerminContract.TerminEntry.COLUMN_NAME_DATUM + " TEXT," +
                    "is_synced INTEGER DEFAULT 0)"; // Neue Spalte für den Synchronisationsstatus


    // Die SQL-Anweisung zum Löschen der Tabelle, falls sie bereits existiert.
    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TerminContract.TerminEntry.TABLE_NAME;

    public TerminDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    // Methode zum Einfügen eines neuen Termins
    public long insertTermin(String name, String ahvNummer, String terminTyp, String details, String terminDatum) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Überprüfen, ob bereits ein Termin existiert
        if (terminExists(terminTyp, terminDatum)) {
            // Termin existiert bereits, daher Fehlschlag der Methode
            return -1;
        }

        ContentValues values = new ContentValues();
        values.put(TerminContract.TerminEntry.COLUMN_NAME_NAME, name);
        values.put(TerminContract.TerminEntry.COLUMN_NAME_AHV_NUMMER, ahvNummer);
        values.put(TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP, terminTyp);
        values.put(TerminContract.TerminEntry.COLUMN_NAME_DETAILS, details);
        values.put(TerminContract.TerminEntry.COLUMN_NAME_DATUM, terminDatum);

        // Insert the new row, returning the primary key value of the new row
        long newRowId = db.insert(TerminContract.TerminEntry.TABLE_NAME, null, values);
        return newRowId;
    }

    private boolean terminExists(String terminTyp, String terminDatum) {
        SQLiteDatabase db = this.getReadableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);
        try {
            Date terminDate = sdf.parse(terminDatum);
            // Berechnen des Zeitfensters für die Überprüfung auf bestehende Termine
            Calendar cal = Calendar.getInstance();
            cal.setTime(terminDate);
            cal.add(Calendar.MINUTE, -30); // 30 Minuten vor dem geplanten Termin
            String lowerBound = sdf.format(cal.getTime());
            cal.add(Calendar.MINUTE, 60); // Gesamtintervall von 60 Minuten, um Termine davor und danach einzuschließen
            String upperBound = sdf.format(cal.getTime());

            String selection = TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP + " = ? AND " +
                    TerminContract.TerminEntry.COLUMN_NAME_DATUM + " BETWEEN ? AND ?";
            String[] selectionArgs = { terminTyp, lowerBound, upperBound };

            Cursor cursor = db.query(
                    TerminContract.TerminEntry.TABLE_NAME,
                    new String[]{TerminContract.TerminEntry._ID}, // Nur die ID wird benötigt für diese Überprüfung
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            boolean exists = cursor.getCount() > 0;
            cursor.close();
            return exists;
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String findNextAvailableTime(String terminTyp, String requestedDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);
        Calendar calRequest = Calendar.getInstance();
        Calendar calNextAvailable = Calendar.getInstance();
        Date requestTime;
        try {
            requestTime = sdf.parse(requestedDate);
            calRequest.setTime(requestTime);
            calNextAvailable.setTime(requestTime);
        } catch (ParseException e) {
            e.printStackTrace();
            return "Fehler beim Parsen des Datums";
        }

        // Suche nach existierenden Terminen am selben Tag
        String selection = TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP + " = ? AND " +
                "date(" + TerminContract.TerminEntry.COLUMN_NAME_DATUM + ") = date(?)";
        String[] selectionArgs = { terminTyp, sdf.format(requestTime) };

        Cursor cursor = db.query(
                TerminContract.TerminEntry.TABLE_NAME,
                new String[]{TerminContract.TerminEntry.COLUMN_NAME_DATUM},
                selection,
                selectionArgs,
                null,
                null,
                TerminContract.TerminEntry.COLUMN_NAME_DATUM + " ASC"
        );

        Date lastEndTime = null;

        if (cursor.moveToFirst()) {
            do {
                Date dbTime;
                try {
                    dbTime = sdf.parse(cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_DATUM)));
                    Calendar calDB = Calendar.getInstance();
                    calDB.setTime(dbTime);
                    calDB.add(Calendar.MINUTE, 30); // Füge 30 Minuten zum DB-Termin hinzu, um das Ende des Termins zu finden

                    // Wenn das Ende des DB-Termins nach der Anfragezeit liegt
                    if (calDB.getTime().after(requestTime)) {
                        // Und wenn kein letztes Ende gesetzt wurde oder das Ende des DB-Termins nach dem letzten Ende liegt
                        if (lastEndTime == null || calDB.getTime().after(lastEndTime)) {
                            lastEndTime = calDB.getTime();
                        }
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        // Wenn ein letztes Ende gesetzt wurde, setze den nächsten verfügbaren Termin 30 Minuten danach
        if (lastEndTime != null) {
            calNextAvailable.setTime(lastEndTime);
        } else {
            // Wenn keine Termine vorhanden sind, die die Anfrage schneiden, ist der nächste verfügbare Termin 30 Minuten nach der Anfragezeit
            calNextAvailable.add(Calendar.MINUTE, 30);
        }

        return sdf.format(calNextAvailable.getTime());
    }

    // Methode zum Abrufen aller Termine
    public Cursor getAllTermine() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TerminContract.TerminEntry.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    // Methode zum Löschen eines Termins
    public void deleteTermin(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = TerminContract.TerminEntry._ID + " LIKE ?";
        String[] selectionArgs = { String.valueOf(id) };
        db.delete(TerminContract.TerminEntry.TABLE_NAME, selection, selectionArgs);
    }

    public Cursor getTermineByType(String terminTyp) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP + " = ?";
        String[] selectionArgs = { terminTyp };

        return db.query(
                TerminContract.TerminEntry.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null);
    }

    public Cursor getTermineByDate(String date) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Verwenden Sie den LIKE-Operator, um nach einem Datumsteil zu suchen, der den String enthält
        String selection = TerminContract.TerminEntry.COLUMN_NAME_DATUM + " LIKE ?";
        String[] selectionArgs = { "%" + date + "%" };

        return db.query(
                TerminContract.TerminEntry.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );
    }
    public List<Termin> getAllUnsyncedTermine() {
        List<Termin> unsyncedTermine = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TerminContract.TerminEntry.TABLE_NAME, null, "is_synced=?", new String[]{"0"}, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                Termin termin = new Termin();
                // ID setzen
                termin.setId(cursor.getLong(cursor.getColumnIndex(TerminContract.TerminEntry._ID)));
                // Alle erforderlichen Felder aus der Datenbank lesen
                termin.setName(cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_NAME)));
                termin.ahvNummer = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_AHV_NUMMER));
                termin.terminTyp = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP));
                termin.details = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_DETAILS));
                termin.terminDatum = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_DATUM));

                unsyncedTermine.add(termin);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return unsyncedTermine;
    }

    public void updateTerminSyncStatus(long terminId, boolean isSynced) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_synced", isSynced ? 1 : 0);
        String selection = TerminContract.TerminEntry._ID + " = ?";
        String[] selectionArgs = { String.valueOf(terminId) };
        db.update(TerminContract.TerminEntry.TABLE_NAME, values, selection, selectionArgs);
    }

    public void saveAppointmentsToDb(List<Termin> terminList) {
        for (Termin termin : terminList) {
            // Verwenden Sie insertTermin, um jeden Termin zu speichern, wenn er nicht existiert
            if (insertTermin(termin.getName(), termin.getAhvNummer(), termin.getTerminTyp(), termin.getDetails(), termin.getDatum()) == -1) {
                // Der Termin existiert bereits, aktualisieren Sie den Synchronisationsstatus
                updateTerminSyncStatus(termin.getId(), true);
            }
        }
    }


}
