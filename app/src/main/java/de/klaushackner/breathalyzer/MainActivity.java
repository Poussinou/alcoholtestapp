package de.klaushackner.breathalyzer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private final DecimalFormat format = new DecimalFormat();
    private boolean doubleBackToExitPressedOnce;
    private MainActivity ma;
    private Context c;
    private User currentUser;
    private TextView tvName;
    private TextView tvAge;
    private TextView tvWeight;
    private TextView tvHeight;
    private TextView tvSex;
    private TextView tvBac;
    private Menu menu;
    private Handler mHandler = new Handler();
    private DrinkAdapter dA;
    private TimerTask timerTask; //updates the gui

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        c = getApplicationContext();
        ma = this;
        format.setDecimalSeparatorAlwaysShown(false);

        tvName = findViewById(R.id.name);
        tvAge = findViewById(R.id.age);
        tvWeight = findViewById(R.id.weight);
        tvHeight = findViewById(R.id.height);
        tvSex = findViewById(R.id.sex);
        Button btnAddDrink = findViewById(R.id.add_drink_button);
        ListView drinks = findViewById(R.id.drinks);
        tvBac = findViewById(R.id.bac);

        btnAddDrink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDrink();
            }
        });

        dA = new DrinkAdapter(this, new ArrayList<Drink>());
        drinks.setAdapter(dA);
        drinks.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final int pos = position;

                AlertDialog.Builder builder = new AlertDialog.Builder(ma);
                builder.setTitle(R.string.dialog_drink_question);
                builder.setPositiveButton(R.string.remove_drink, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Drink d = currentUser.drinks.get(pos);
                        currentUser.removeDrink(d.consumePoint);
                        currentUser.saveUser(c);
                        updateGui();
                    }
                });
                builder.setNeutralButton(R.string.add_as_mixture, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Drink d = currentUser.drinks.get(pos);
                        //TODO Handle mixture saving

                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            }
        });

        ImageButton refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateGui();
            }
        });

        if (getIntent().getBooleanExtra("fromShowRecipes", false)) {
            switchUser(getIntent().getLongExtra("currentUser", 0));
        } else {
            switchUser(isStartedByLauncher());
        }

        //If coming from a notication, the mixture will be added to the current user
        /*String m = getIntent().getStringExtra("mixtureToAdd");
        if (m != null) {
            try {
                currentUser.consumeDrink(new Mixture(new JSONObject(m));
                currentUser.saveUser(c);
                updateGui();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }*/
    }

    protected boolean isStartedByLauncher() {
        if (getIntent() == null) {
            return false;
        }
        boolean isActionMain = Intent.ACTION_MAIN.equals(getIntent().getAction());
        Set<String> categories = getIntent().getCategories();
        boolean isCategoryLauncher = categories != null && categories.contains(Intent.CATEGORY_LAUNCHER);
        return isActionMain && isCategoryLauncher;
    }

    /**
     * Updates gui after app is paused
     */
    @Override
    public void onResume() {
        super.onResume();
        updateGui();
    }

    /**
     * Updates gui on startup
     */
    @Override
    public void onStart() {
        super.onStart();
        updateGui();
    }

    /**
     * Stops timer when pausing MainActivity
     */
    @Override
    public void onPause() {
        super.onPause();
        stopTimer();
    }


    /**
     * Adds drinks from the current person to the list view
     * Calculates current persons blood alcohol content (BAC or in German: BAK)
     * Only called by updateGui();
     */
    private void updateDrinkList() {
        dA.clear();
        dA.notifyDataSetChanged();
        ArrayList<Drink> drinks = currentUser.drinks;
        double totalBac = 0;

        //removing old drinks
        if (!drinks.isEmpty()) {
            /*
            If you have 2 items in your list and remove the first one, index 1 is gone and the for each loop throws an
            error. Therefore I will remove all "old drinks" after looping through the list
            */
            ArrayList<Drink> toRemove = new ArrayList<>();
            for (Drink d : drinks) {
                if (d.consumePoint + d.depletingDuration <= System.currentTimeMillis()) {
                    toRemove.add(d);
                }
            }
            drinks.removeAll(toRemove);
        }

        if (!drinks.isEmpty()) {
            long totalDepletionDuration = drinks.get(0).consumePoint; //starting with the consume point of the first trink
            boolean firstDrink = false;

            for (Drink d : drinks) {
                totalDepletionDuration = totalDepletionDuration + d.depletingDuration; //adding depletion duration of current drink to the total depletion duration

                if (firstDrink) {
                    totalBac = totalBac + d.getBac();
                } else {
                    totalBac = totalBac + d.getRelativeBac();
                    firstDrink = true;
                }

                d.setDepletionPoint(totalDepletionDuration); //setting the depletion point to the consumePoint from the first drink + all n drink's depletionDurations
                dA.add(d);
            }
        }

        //Updating the bac of the current user after calculating the total bac
        //At startup it is negative (for some reason)
        if (totalBac >= 0) {
            tvBac.setText(String.format("%s %s", format.format(totalBac), getResources().getString(R.string.per_mille)));
        }

        //Saving user in case there was a depleted drink removed
        currentUser.saveUser(this);
    }


    /**
     * Creates a user and opens select dialog afterwards
     */
    private void createUser() {
        stopTimer();
        startActivity(new Intent(this, CreateUser.class));
    }

    /**
     * Remove given user. If that's the only user, the activity to create a new one will be launched.
     * If there were two users, the one left will be selected
     *
     * @param userToRemove user to remove
     */
    private void removeUser(User userToRemove) {
        stopTimer();
        SharedPreferences sharedPref = getSharedPreferences("data", 0);
        SharedPreferences.Editor editor = sharedPref.edit();
        JSONArray users;
        try {
            users = new JSONArray(sharedPref.getString("users", "[]"));

            for (int i = 0; i < User.getUserCount(c); i++) {
                JSONObject user = new JSONObject(users.get(i).toString());
                if (user.getString("name").compareTo(userToRemove.name) == 0) {
                    users.remove(i);
                    editor.putString("users", users.toString());
                    editor.commit();

                    if (User.getUserCount(c) == 0) {
                        createUser();
                        return;
                    }

                    if (User.getUserCount(c) == 1) {
                        currentUser = new User(new JSONObject(users.get(0).toString()));
                        updateGui();
                    } else {
                        switchUser(false);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * Adds mixtures to dialog and handles its events
     * Here you can addCustomRecipe more mixtures
     */
    private void addDrink() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_drink);

        dialog.setTitle(R.string.add_drink);
        final TextView title = dialog.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            title.setPadding(0, 20, 0, 20);
        }

        final MixtureAdapter mixtureAdapter = new MixtureAdapter(this, new ArrayList<Mixture>());
        GridView mixtureList = dialog.findViewById(R.id.mixtureList);
        mixtureList.setAdapter(mixtureAdapter);

        final ArrayList<Mixture> mixtures = Mixture.getMixtureArray(currentUser);

        for (Mixture aMixtureArray : mixtures) {
            mixtureAdapter.add(aMixtureArray);
        }

        mixtureList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                if (mixtures.get(position).getAlcContent() == 0) {
                    addCustomDrink(0, null);
                    dialog.dismiss();
                    return;
                }

                currentUser.consumeDrink(mixtures.get(position));
                currentUser.saveUser(c);

                dialog.dismiss();
                updateGui();
            }
        });
        mixtureList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final int pos = position;
                final Dialog d = dialog;

                AlertDialog.Builder builder = new AlertDialog.Builder(ma);
                builder.setTitle(R.string.remove_custom_mixture);
                builder.setPositiveButton(R.string.remove_mixture, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //Mixture m = mixtures.get(pos));
                        //Mixture.removeCustomMixture(c, m);
                        d.dismiss();

                    }
                });

                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                builder.create().show();
                return true;
            }
        });

        dialog.show();
    }

    /**
     * @param stage         0 = from "addCustomRecipe drinks" dialog; 1 = from "addCustomRecipe custom drink" dialog
     * @param customMixture from "addCustomRecipe custom drink" dialog
     */
    private void addCustomDrink(int stage, Mixture customMixture) {
        switch (stage) {
            case 0: //0 = from "addCustomRecipe drinks" dialog; 1 = from "addCustomRecipe custom drink" dialog
                //Open dialog and wait for result
                final Dialog d = new Dialog(this);
                d.setContentView(R.layout.dialog_add_custom_drink);

                final ImageView image = d.findViewById(R.id.image);
                MixtureImage currentImage;

                if (currentUser.name.compareTo("Franzi") == 0) {
                    currentImage = MixtureImage.custom_panda;
                    image.setImageResource(getResources().getIdentifier(currentImage.toString(), "mipmap",
                            getApplicationContext().getPackageName()));
                } else {
                    currentImage = MixtureImage.custom;
                    image.setImageResource(getResources().getIdentifier(currentImage.toString(), "mipmap",
                            getApplicationContext().getPackageName()));
                }

                image.setTag(currentImage);

                image.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Dialog dialog = new Dialog(ma);
                        dialog.setContentView(R.layout.dialog_switch_mixtureimage);

                        dialog.setTitle(R.string.switch_mixtureimage);
                        final TextView title = dialog.findViewById(android.R.id.title);
                        if (title != null) {
                            title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                            title.setPadding(0, 20, 0, 20);
                        }

                        final MixtureImageAdapter mA = new MixtureImageAdapter(ma, new ArrayList<MixtureImage>());
                        GridView mixtureList = dialog.findViewById(R.id.mixtureList);
                        mixtureList.setAdapter(mA);

                        final MixtureImage[] mixtureItemArray = MixtureImage.values();

                        for (MixtureImage aMixtureItemArray : mixtureItemArray) {
                            mA.add(aMixtureItemArray);
                        }

                        mixtureList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                                MixtureImage m = mixtureItemArray[position];

                                image.setTag(m); //Updates tag if necessary
                                image.setImageResource(getResources().getIdentifier(m.toString(), "mipmap",
                                        getApplicationContext().getPackageName()));
                                dialog.dismiss();
                            }
                        });
                        dialog.show();
                    }
                });

                Button addDrink = d.findViewById(R.id.addDrink);
                addDrink.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TextView tvName = d.findViewById(R.id.name);
                        TextView tvAmount = d.findViewById(R.id.amount);
                        TextView tvPercentage = d.findViewById(R.id.percentage);

                        String name = tvName.getText().toString();
                        double amount = Double.parseDouble("0" + tvAmount.getText());
                        double percentage = Double.parseDouble("0" + tvPercentage.getText()) / 100;

                        if (Mixture.isValidMixture(name, amount, percentage)) {
                            MixtureImage m = MixtureImage.fromString(image.getTag().toString());
                            if (currentUser.name.compareTo("Franzi") == 0) {
                                addCustomDrink(1, new Mixture(name, "Custom drink", amount, percentage, m));
                            } else {
                                addCustomDrink(1, new Mixture(name, "Custom drink", amount, percentage, m));
                            }
                            d.dismiss();
                        } else {
                            Toast.makeText(c, c.getResources().getString(R.string.wronginput), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                d.show();
                break;
            case 1: //from "addCustomRecipe custom drink" dialog
                if (customMixture != null || currentUser != null) {
                    currentUser.consumeDrink(customMixture);
                    currentUser.saveUser(c);
                    updateGui();
                }
                break;
        }
    }


    /**
     * Opens activity to edit the current user
     */
    private void editUser() {
        stopTimer();
        //Save old user before selecting a new one
        currentUser.saveUser(c);

        Intent i = new Intent(this, EditUser.class);
        i.putExtra("created", currentUser.created);
        startActivity(i);
    }

    /**
     * Switches between users. If there's no user, a new one will be created. If only one user exists, a toast will be shown
     *
     * @param fromStart If this method is called in the onCreate() method, the "only one user existing" message will be suppressed.
     */
    private void switchUser(boolean fromStart) {
        closeOptionsMenu();
        try {
            SharedPreferences sharedPref = getSharedPreferences("data", 0);
            final SharedPreferences.Editor editor = sharedPref.edit();

            //Ist die Users-datenbank leer, wird ein neuer User erstellt
            if (sharedPref.getString("users", "[]").compareTo("[]") == 0) {
                createUser();
            } else {
                final JSONArray users = new JSONArray(sharedPref.getString("users", "[]"));

                //Select last user on startup
                if (fromStart) {
                    long lastUser = sharedPref.getLong("lastUser", 0);

                    if (lastUser != 0) {
                        for (int i = 0; i < User.getUserCount(c); i++) {
                            User u = new User(new JSONObject(users.get(i).toString()));
                            if (u.created == lastUser) {
                                currentUser = u;
                                editor.putLong("lastUser", u.created);
                                editor.commit();
                                return;
                            }
                        }
                    }
                }

                //Wenn nur ein User vorhanden, wird dieser ausgewählt
                if (User.getUserCount(c) == 1) {
                    currentUser = new User(new JSONObject(users.get(0).toString()));
                    editor.putLong("lastUser", currentUser.created);
                    editor.commit();
                    return;
                }

                //Dialog with all users
                final ArrayList<User> usersList = new ArrayList<>();
                for (int i = 0; i < User.getUserCount(c); i++) {
                    usersList.add(new User(new JSONObject(users.get(i).toString())));
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.pick_user);

                if (currentUser == null) {
                    builder.setCancelable(false);
                }

                builder.setNeutralButton(R.string.add_user, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createUser();
                    }
                });

                builder.setAdapter(new UserAdapter(getApplicationContext(), usersList),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int item) {
                                currentUser = usersList.get(item);
                                //For some reason "R.string.you_selected" doesn't work anymore, maybe because I changed MainActivity.this to c
                                Toast.makeText(c, getResources().getString(R.string.you_selected) + " " + currentUser.name, Toast.LENGTH_LONG).show();
                                editor.putLong("lastUser", currentUser.created);
                                editor.commit();

                                updateGui();
                                dialog.dismiss();
                            }
                        });

                Dialog dialog = builder.create();
                dialog.show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void switchUser(long created) {
        if (created != 0) {
            currentUser = User.getUserByCreated(c, created);
        } else {
            switchUser(isStartedByLauncher());
        }
    }

    /**
     * Updates GUI to match the current selected user, then calls updateDrinkList()
     */
    public void updateGui() {
        if (currentUser != null) {
            tvName.setText(currentUser.name);
            tvAge.setText(format.format(currentUser.age) + " " + getString(R.string.years));
            tvWeight.setText(format.format(currentUser.weight) + " kg");
            tvHeight.setText(format.format(currentUser.height) + " cm");

            if (currentUser.isMale) {
                tvSex.setText(R.string.male);
            } else {
                tvSex.setText(R.string.female);
            }

            if (User.getUserCount(c) == 1) {
                try {
                    menu.removeItem(R.id.switchUser);
                } catch (Exception e) {
                    //Nobody cares about you, e.
                }
            }

            updateDrinkList();
            startTimer();
        }
    }

    /**
     * starts the timer which updates the gui every 10 seconds
     * it will call a stopTimer() before starting a new timer
     */
    private void startTimer() {
        Timer timer = new Timer();
        stopTimer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                mHandler.postDelayed(hMyTimeTask, 0); //delay 0 seconds
            }
        };
        timer.schedule(timerTask, 0, 10000);
    }

    private void stopTimer() {
        mHandler.removeCallbacks(hMyTimeTask);
        try {
            timerTask.cancel();
        } catch (Exception e) {
            //can be null at first call
        }
    }

    private Runnable hMyTimeTask = new Runnable() {
        public void run() {
            updateDrinkList();
        }
    };


    /**
     * Layout-Stuff
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.switchUser);
        if (m != null) {
            m.setIcon(R.mipmap.switchuser);
        }

        m = menu.findItem(R.id.newUser);
        m.setIcon(R.mipmap.male_new);
        m = menu.findItem(R.id.editUser);
        m.setIcon(R.mipmap.male_edit);
        m = menu.findItem(R.id.removeUser);
        m.setIcon(R.mipmap.male_delete);
        if (currentUser != null) { //After creating a user while showing the menu, currentUser is null
            if (!currentUser.isMale) {
                m = menu.findItem(R.id.newUser);
                m.setIcon(R.mipmap.female_new);
                m = menu.findItem(R.id.editUser);
                m.setIcon(R.mipmap.female_edit);
                m = menu.findItem(R.id.removeUser);
                m.setIcon(R.mipmap.female_delete);
            }
        }

        if (User.getUserCount(c) == 1) {
            menu.removeItem(R.id.switchUser);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switchUser:
                switchUser(false);
                break;
            case R.id.editUser:
                editUser();
                break;
            case R.id.removeUser:
                removeUser(currentUser);
                break;
            case R.id.newUser:
                createUser();
                break;
            case R.id.sendFeedback:
                startActivity(new Intent(this, SendFeedback.class));
                break;/*
            case R.id.recipes:
                Intent i = new Intent(this, ShowRecipes.class);
                i.putExtra("currentUser", currentUser.getCreated());
                startActivity(i);
                break;*/
            case R.id.about:
                final Dialog dialog = new Dialog(this);
                dialog.setContentView(R.layout.dialog_about);
                dialog.show();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.press_back_again_to_leave, Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 1700);
    }
}

