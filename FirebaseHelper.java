package com.example.sendhelpapplication;

import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class FirebaseHelper {
    private DatabaseReference mDatabase;

    public FirebaseHelper() {
        // Initialisiere die DatabaseReference mit der spezifischen URL deiner Firebase-Datenbank
        mDatabase = FirebaseDatabase.getInstance("https://sendhelpapp-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    public void addTerminToFirebase(Termin termin, FirebaseCallback callback) {
        mDatabase.child("termine").push().setValue(termin, (databaseError, databaseReference) -> {
            if (databaseError != null) {
                callback.onComplete(false);
            } else {
                callback.onComplete(true);
            }
        });
    }

    public interface FirebaseDatabaseCompletionListener {
        void onComplete(boolean successful);
    }

    public void readTermineFromFirebase(ValueEventListener listener) {
        // Setze einen ValueEventListener, der auf Änderungen in der "termine" Kinder-Referenz hört
        mDatabase.child("termine").addValueEventListener(listener);
    }

    public interface FirebaseCallback {
        void onComplete(boolean success);
    }

    public void fetchAllAppointmentsFromFirebase(TerminDbHelper dbHelper) {
        mDatabase.child("termine").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<Termin> terminList = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Termin termin = snapshot.getValue(Termin.class);
                    terminList.add(termin);
                }
                // Speichern der Termine in der lokalen DB
                dbHelper.saveAppointmentsToDb(terminList);
                // Aktualisiere UI nach erfolgreichem Speichern
                MainActivity.updateCalendarWithAppointments();
                //MainActivity.Toast.makeText(MainActivity.this, "Keine Termine für dieses Datum.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.out.println("Die Daten konnten nicht abgerufen werden: " + databaseError.getCode());
            }
        });
    }

}
