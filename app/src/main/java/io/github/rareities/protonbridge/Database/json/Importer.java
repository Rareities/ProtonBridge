package io.github.rareities.protonbridge.Database.json;


import android.content.Context;

import org.json.JSONException;

import io.github.rareities.protonbridge.Database.DatabaseHandler;

public class Importer {


    public static void importJson(String json, Context context) throws JSONException {
        ImportPlan plan = ImportPlan.parse(json, context);
        io.github.rareities.protonbridge.util.BisyncPolicy.Admission admission =
                io.github.rareities.protonbridge.util.BisyncPolicy.Admission.Companion.acquire(context);
        if (admission == null) throw new JSONException("A transfer is active. Finish or cancel it before restoring tasks.");
        try {
            new DatabaseHandler(context).replaceAll(plan.getTasks(), plan.getFilters(), plan.getTriggers());
        } finally {
            admission.close();
        }
    }

}
