package com.example.llmwithrag;

import static android.Manifest.permission.RECORD_AUDIO;
import static com.example.llmwithrag.Utils.getCoordinatesFromReadableAddress;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.llmwithrag.llm.CompletionMessage;
import com.example.llmwithrag.llm.CompletionRequest;
import com.example.llmwithrag.llm.CompletionResponse;
import com.example.llmwithrag.llm.OpenAiService;
import com.example.llmwithrag.llm.RetrofitClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PerformQueryFragment extends Fragment {
    private static final String TAG = PerformQueryFragment.class.getSimpleName();
    private ServiceViewModel mViewModel;
    private IMonitoringService mService;
    private TextView mResultDisplay;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> speechResultLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_perform_query, container, false);
        mViewModel = new ViewModelProvider(requireActivity()).get(ServiceViewModel.class);
        mViewModel.getService().observe(getViewLifecycleOwner(), service -> mService = service);

        initResultLaunchers();

        Button startVoiceRecognitionButton = view.findViewById(R.id.startVoiceRecognitionButton);
        startVoiceRecognitionButton.setOnClickListener(view1 -> {
            if (isPermissionGranted()) {
                startVoiceRecognition();
            } else {
                if (shouldShowRequestPermissionRationale(RECORD_AUDIO)) {
                    showRationaleDialog();
                } else {
                    requestPermissionLauncher.launch(RECORD_AUDIO);
                }
            }
        });

        EditText queryInput = view.findViewById(R.id.queryInput);
        Button queryButton = view.findViewById(R.id.performQueryButton);
        mResultDisplay = view.findViewById(R.id.resultDisplay);
        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = queryInput.getText().toString();
                queryInput.setText("");
                hideKeyboardFromFragment();
                mResultDisplay.setText(R.string.wait_answer);
                Log.i(TAG, "perform query of " + text);
                performQuery(text);
            }
        });
        return view;
    }

    private void initResultLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        startVoiceRecognition();
                    } else {
                        Toast.makeText(getContext(),
                                "Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                });

        speechResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == FragmentActivity.RESULT_OK &&
                            result.getData() != null) {
                        ArrayList<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String query = matches.get(0);
                            Log.i(TAG, "query from voice: " + query);
                            performQuery(query);
                        }
                    }
                });
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        speechResultLauncher.launch(intent);
    }

    private boolean isPermissionGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showRationaleDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Permission Needed")
                .setMessage("This permission is needed for voice recognition features")
                .setPositiveButton("OK", (dialog, which) -> requestPermissionLauncher.launch(RECORD_AUDIO))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void hideKeyboardFromFragment() {
        Activity activity = getActivity();
        if (activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            View view = activity.getCurrentFocus();
            if (view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private String runNaviApp(String destination) {
        try {
        /*
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity"));
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=r"));
        startActivity(intent);
         */

            Uri uri = null;
            boolean foundLocation = false;
            String[] parts = destination.split(",\\s*");
            if (parts.length == 2) {
                foundLocation = true;
            } else {
                Log.i(TAG, "-> destination : " + destination);
                destination = getCoordinatesFromReadableAddress(
                        getContext().getApplicationContext(), destination);
                Log.i(TAG, "<- destination : " + destination);
                foundLocation = !TextUtils.isEmpty(destination);
            }

            if (!foundLocation) {
                Log.i(TAG, "Unable to find the location");
                Toast.makeText(getContext(), "Unable to find the location", Toast.LENGTH_LONG).show();
            } else {
                parts = destination.split(",\\s*");
                double latitude = Double.parseDouble(parts[0]);
                double longitude = Double.parseDouble(parts[1]);
                uri = Uri.parse("tmap://route?goalx=" + longitude + "&goaly=" + latitude + "&name=home");

                Toast.makeText(getContext(), "destination: " + destination, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        } catch (ActivityNotFoundException e1) {
            redirectToPlayStore("com.skt.tmap.ku");
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return "";
    }

    private void redirectToPlayStore(String appPackageName) {
        try {
            // Try to open the Play Store app
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            // If the Play Store app is not installed, open the web browser
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    private void performQuery(String query) {
        if (mService == null) {
            Toast.makeText(getContext(), "Service Is Not Ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (BuildConfig.IS_SCHEMA_ENABLED) {
            performQueryWithSchema(query);
        } else {
            performQueryWithoutSchema(query);
        }
    }

    private String adjustResponse(String response) {
        if (response.startsWith("```json")) {
            response = response.substring(7).trim();
        }

        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3).trim();
        }

        return response;
    }

    private void performQueryWithSchema(String query) {
        final String originalQuery = query;
        final List<CompletionMessage> messages = new ArrayList<>();
        query = generateQuery(query, mService.getSchema(), null);
        final String modifiedQuery = query;
        Log.i(TAG, "query: " + query);

        messages.add(new CompletionMessage("user", query));
        CompletionRequest request = new CompletionRequest("gpt-4o", messages);
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<CompletionResponse> call = service.getCompletions(request);
        call.enqueue(new Callback<CompletionResponse>() {
            @Override
            public void onResponse(Call<CompletionResponse> call, Response<CompletionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String completion = response.body().choices.get(0).message.content;
                    completion = adjustResponse(completion);
                    Log.i(TAG, "converted user query as to the schema: " + completion);

                    List<String> result = mService.findSimilarOnes(modifiedQuery, completion);
                    String query = generateQuery(originalQuery, null, result);
                    Log.i(TAG, "augmented query: " + query);

                    messages.clear();
                    messages.add(new CompletionMessage("user", query));
                    CompletionRequest request = new CompletionRequest("gpt-4o", messages);
                    Call<CompletionResponse> _call = service.getCompletions(request);
                    _call.enqueue(new Callback<CompletionResponse>() {
                        private String extractGeoLocation(String answer) {
                            String[] lines = answer.split("\n");
                            String line = lines[lines.length - 1];
                            return line != null ? line.trim().replaceAll("^`+|`+$", "") : "";
                        }

                        @Override
                        public void onResponse(Call<CompletionResponse> call, Response<CompletionResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                String completion = response.body().choices.get(0).message.content;
                                Log.i(TAG, "response from the llm: " + completion);

                                mResultDisplay.setText(runNaviApp(extractGeoLocation(completion)));
                            }
                        }

                        @Override
                        public void onFailure(Call<CompletionResponse> call, Throwable t) {
                            Log.e(TAG, "failed in performing query: " + t.getMessage());
                            mResultDisplay.setText(t.getMessage());
                        }
                    });

                }
            }

            @Override
            public void onFailure(Call<CompletionResponse> call, Throwable t) {
                Log.e(TAG, "failed in performing query: " + t.getMessage());
                mResultDisplay.setText(t.getMessage());
            }
        });
    }

    private void performQueryWithoutSchema(String query) {
        List<CompletionMessage> messages = new ArrayList<>();
        List<String> result = mService.findSimilarOnes(query);
        if (!result.isEmpty()) {
            query = generateQuery(query, result);
        }

        Log.i(TAG, "query: " + query);

        messages.add(new CompletionMessage("user", query));
        CompletionRequest request = new CompletionRequest("gpt-4-turbo", messages);
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<CompletionResponse> call = service.getCompletions(request);
        call.enqueue(new Callback<CompletionResponse>() {
            @Override
            public void onResponse(Call<CompletionResponse> call, Response<CompletionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String completion = response.body().choices.get(0).message.content;
                    Log.i(TAG, "response: " + completion);
                    mResultDisplay.setText(completion);
                }
            }

            @Override
            public void onFailure(Call<CompletionResponse> call, Throwable t) {
                Log.e(TAG, "failed in performing query: " + t.getMessage());
                mResultDisplay.setText(t.getMessage());
            }
        });
    }

    private String generateQuery(String query, String schema, List<String> results) {
        StringBuilder sb = new StringBuilder("My query is \"" + query + "\".");
        if (results == null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sb.append("\nToday is ").append(sdf.format(new Date()));
            sb.append("\nAnd here's schema : ").append(schema);
            sb.append("\nGo through the user's query and just rebuild it in the form of given schema and don't try to answer or take any other action.");
            sb.append("\nEnsure you provide only json-formatted string, and do not add any other comments");
            sb.append("\nIn the bracket, 'entities' MUST be at the top level of the hierarchy as the schema indicates.");
        } else {
            sb.append("\nAnd also here are relevant context.");
            for (String result : results) {
                sb.append("\n").append(result);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sb.append("\nToday is ").append(sdf.format(new Date()));
            sb.append("\nIdentify and correlate all the entities based on the given context.");
            sb.append("\nDO NOT correlate an entity to a location which doesn't have explicit relationship.");
            sb.append("\nThe photo provided might have been taken at an earlier date and is intended for reference for the upcoming event.");

            sb.append("\nyou MUST provide a step-by-step explanation of your reasoning in determining the location.");
            sb.append("\nClearly state if there is no direct mention or involvement of the user in the event or message.");
            sb.append("\nIf there are multiple locations found, you MUST clearly mention why one of them was determined as an answer over other ones.");

            sb.append("\nIf no location meets all conditions, respond with \"Unable to find the location.\"");
            sb.append("\nIf a location is found, it MUST be on a new single line and formatted either exactly as 'latitude, longitude' or name of the location if the former is unavailable.");
            sb.append("\nDo not put any further lines after that.");
        }
        return sb.toString();
    }

    @NonNull
    private String generateQuery(String query, List<String> results) {
        StringBuilder sb = new StringBuilder("my query is \"" + query + "\".");
        sb.append("\nFirst, figure out if I'm asking you to find the route to or would like to go to the location either implicitly or explicitly.");
        sb.append("\nIf it is not, just tell me \"unable to find the location\".");
        sb.append("\nOtherwise, please go through my activities throughout the day thoroughly.");
        for (String result : results) {
            sb.append("\n").append(result);
        }
        sb.append("\n").append("All the addresses found above are in the form of 'latitude,longitude'.");
        sb.append("\n").append("Out of all the addresses, find the one that is most likely the location which I've mentioned in the query.");
        sb.append("\n").append("Please note that 'home' or '집' refers to the location where I sleep and 'office' or '회사' refers to the location where I work.");
        sb.append("\n").append("Ensure you provide the answer in the form of 'latitude, longitude' only, and do not add any other comments.");
        return sb.toString();
    }
}
