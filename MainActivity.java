package com.example.sendhelpapplication;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.appcompat.app.AppCompatActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import android.widget.ArrayAdapter;
import android.widget.Toast;



public class MainActivity extends AppCompatActivity {

    // Klassenvariablen
    EditText editTextName, editTextAHVNummer, editTextDetails;
    Spinner spinnerTerminTyp;
    TextView textViewTermin;
    Button buttonReservieren, buttonAbbrechen;
    Calendar calendar;
    private static TerminDbHelper dbHelper;
    CalendarView calendarView;
    static HashSet<String> datesWithAppointments;
    FirebaseHelper firebaseHelper = new FirebaseHelper();

    private BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isOnline()) {
                synchronizeLocalDataWithFirebase();
            }
        }
    };

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(networkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(networkStateReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new TerminDbHelper(this);
        // Registrieren des BroadcastReceivers, um auf Netzwerkänderungen zu reagieren
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStateReceiver, filter);

        // Erstmalige Synchronisation beim Start der App, falls online
        if (isOnline()) {
            synchronizeLocalDataWithFirebase();
        }
        dbHelper = new TerminDbHelper(this);

        editTextName = findViewById(R.id.editTextName);
        editTextAHVNummer = findViewById(R.id.editTextAHVNummer);
        editTextDetails = findViewById(R.id.editTextDetails);
        spinnerTerminTyp = findViewById(R.id.spinnerTerminTyp);
        textViewTermin = findViewById(R.id.textViewTermin);
        buttonReservieren = findViewById(R.id.buttonReservieren);
        buttonAbbrechen = findViewById(R.id.buttonAbbrechen);
        calendar = Calendar.getInstance();
        datesWithAppointments = new HashSet<>();
        calendarView = findViewById(R.id.calendarView);
        FirebaseHelper firebaseHelper = new FirebaseHelper();
        TerminDbHelper terminDbHelper = new TerminDbHelper(this);

        firebaseHelper.fetchAllAppointmentsFromFirebase(terminDbHelper);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.termin_typen, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTerminTyp.setAdapter(adapter);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String selectedDate = formatDate(year, month, dayOfMonth);
            if (datesWithAppointments.contains(selectedDate)) {
                showAppointmentsForDate(selectedDate);
            } else {
                Toast.makeText(MainActivity.this, "Keine Termine für dieses Datum.", Toast.LENGTH_SHORT).show();
            }
        });

        textViewTermin.setOnClickListener(v -> new DatePickerDialog(MainActivity.this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show());

        buttonReservieren.setOnClickListener(view -> {
            String name = editTextName.getText().toString();
            String ahvNummer = editTextAHVNummer.getText().toString();
            String details = editTextDetails.getText().toString();
            String terminTyp = spinnerTerminTyp.getSelectedItem().toString();
            String terminDatum = textViewTermin.getText().toString();

            if (name.isEmpty() || ahvNummer.isEmpty() || details.isEmpty() || terminDatum.equals("Auswahl Datum und Uhrzeit:\n01.01.2024 08:00")) {
                Toast.makeText(MainActivity.this, "Bitte füllen Sie alle Felder aus", Toast.LENGTH_SHORT).show();
            } else {
                // Erstellen eines neuen Termin-Objekts
                Termin termin = new Termin(name, ahvNummer, terminTyp, details, terminDatum);

                // Hinzufügen des Termins zur lokalen Datenbank und zur Firebase-Datenbank
                long newId = dbHelper.insertTermin(name, ahvNummer, terminTyp, details, terminDatum);
                if (newId == -1) {
                    Toast.makeText(MainActivity.this, "Terminüberschneidung, Kalender prüfen!", Toast.LENGTH_SHORT).show();
                } else {
                    // ID setzen für sync Status
                    termin.setId(newId);
                    // Termin lokal gespeichert, jetzt zur Firebase hinzufügen
                    firebaseHelper.addTerminToFirebase(termin, new FirebaseHelper.FirebaseCallback() {
                        @Override
                        public void onComplete(boolean success) {
                            if (success) {
                                dbHelper.updateTerminSyncStatus(termin.getId(), true); //Sync Status 1
                                Toast.makeText(MainActivity.this, "Termin gespeichert und synchronisiert.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Fehler beim Speichern in Firebase.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    updateCalendarWithAppointments();
                }
            }
        });


        buttonAbbrechen.setOnClickListener(view -> {
            // Hier Code zum Abbrechen
        });
        updateCalendarWithAppointments();
    }
    // Listener für den DatePickerDialog, um das Datum zu setzen
    private final DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            // Zeit auswählen
            new TimePickerDialog(MainActivity.this, timeSetListener,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE), true).show();
        }
    };

    // Listener für den TimePickerDialog, um die Uhrzeit zu setzen
    private final TimePickerDialog.OnTimeSetListener timeSetListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            // Formatieren Sie das Datum und die Zeit, wie Sie es anzeigen möchten
            @SuppressLint("DefaultLocale") String selectedDate = String.format("%02d.%02d.%d", calendar.get(Calendar.DAY_OF_MONTH),
                    (calendar.get(Calendar.MONTH) + 1), calendar.get(Calendar.YEAR));
            @SuppressLint("DefaultLocale") String selectedTime = String.format("%02d:%02d", hourOfDay, minute);
            textViewTermin.setText(String.format("%s %s", selectedDate, selectedTime));
        }
    };

    // Methode zum Aktualisieren des Kalenders mit Terminen
    static void updateCalendarWithAppointments() {
        Cursor cursor = dbHelper.getAllTermine();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

        datesWithAppointments.clear();
        while (cursor.moveToNext()) {
            String datum = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_DATUM));
            // Try Block für das Parsen des Datums
            try {
                Date parsedDate = sdf.parse(datum);
                if (parsedDate != null) {
                    // Geparstes Datum hinzufügen
                    datesWithAppointments.add(sdf.format(parsedDate));
                }
            } catch (ParseException e) {
                Log.e("MainActivity", "Fehler beim Parsen des Datums", e);
            }
        }
        cursor.close();
    }

    // Methode zum Anzeigen der Termine für ein ausgewähltes Datum
    private void showAppointmentsForDate(String date) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Termine am " + date);

        // Ein LinearLayout für das Dialogfenster erstellen
        LinearLayout layout = new LinearLayout(MainActivity.this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Cursor zur Abfrage der Termine am ausgewählten Datum
        Cursor cursor = dbHelper.getTermineByDate(date);
        if (cursor.getCount() == 0) {
            // Wenn keine Termine vorhanden sind, erscheint ein Popup
            TextView textView = new TextView(MainActivity.this);
            textView.setText("Keine Termine gefunden.");
            textView.setPadding(10, 10, 10, 10);
            layout.addView(textView);
        } else {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_NAME));
                String datum = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_DATUM));
                String termintyp = cursor.getString(cursor.getColumnIndex(TerminContract.TerminEntry.COLUMN_NAME_TERMIN_TYP));

                // Uhrzeit aus Datum extrahieren
                String[] parts = datum.split(" ");
                String uhrzeit = parts.length > 1 ? parts[1] : "Uhrzeit unbekannt";
                // Eine halbe Stunde zur extrahierten Uhrzeit hinzufügen, wenn Uhrzeit bekannt ist
                String terminrange;
                if(!"Uhrzeit unbekannt".equals(uhrzeit)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                    try {
                        Date dateObj = sdf.parse(uhrzeit);
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(dateObj);
                        calendar.add(Calendar.MINUTE, 30);
                        terminrange = sdf.format(calendar.getTime());
                    } catch (ParseException e) {
                        terminrange = "Zeitformat ungültig";
                    }
                } else {
                    terminrange = uhrzeit; // Hier bleibt es bei "Uhrzeit unbekannt"
                }

                // Erstellen Sie TextViews für jeden Termin und fügen Sie sie zum Layout hinzu
                TextView textView = new TextView(MainActivity.this);
                textView.setText(String.format("%s - %s bis %s", termintyp, uhrzeit, terminrange));
                textView.setPadding(10, 10, 10, 10);
                layout.addView(textView);
            }
        }
        cursor.close();

        // Layout wird zum Builder hinzugefügt
        builder.setView(layout);

        // Schaltfläche wird dem Builder hinzugefügt
        builder.setPositiveButton("Ok", (dialog, id) -> dialog.dismiss());

        // Dialog erstellen und anzeigen
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    // Methode zum Datums Konvertieren zu dd.MM.yyyy
    private String formatDate(int year, int month, int dayOfMonth) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, dayOfMonth);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
        return sdf.format(cal.getTime());
    }
    private void synchronizeLocalDataWithFirebase() {
        List<Termin> unsyncedTermine = dbHelper.getAllUnsyncedTermine();
        for (Termin termin : unsyncedTermine) {
            firebaseHelper.addTerminToFirebase(termin, new FirebaseHelper.FirebaseCallback() {
                @Override
                public void onComplete(boolean success) {
                    if (success) {
                        // Aktualisieren Sie hier den Synchronisationsstatus des Termins in der lokalen Datenbank
                        dbHelper.updateTerminSyncStatus(termin.getId(), true);
                    } else {
                        // Fehlerbehandlung, wenn die Synchronisation fehlschlägt
                    }
                }
            });
        }


    }


}