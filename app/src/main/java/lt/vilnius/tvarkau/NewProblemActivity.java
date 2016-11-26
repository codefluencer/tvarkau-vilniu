package lt.vilnius.tvarkau;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.model.LatLng;
import com.viewpagerindicator.CirclePageIndicator;

import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneOffset;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import autodagger.AutoComponent;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.State;
import lt.vilnius.tvarkau.backend.ApiMethod;
import lt.vilnius.tvarkau.backend.ApiRequest;
import lt.vilnius.tvarkau.backend.ApiResponse;
import lt.vilnius.tvarkau.backend.GetNewProblemParams;
import lt.vilnius.tvarkau.backend.LegacyApiModule;
import lt.vilnius.tvarkau.backend.LegacyApiService;
import lt.vilnius.tvarkau.events_listeners.NewProblemAddedEvent;
import lt.vilnius.tvarkau.utils.FormatUtils;
import lt.vilnius.tvarkau.utils.ImageUtils;
import lt.vilnius.tvarkau.utils.KeyboardUtils;
import lt.vilnius.tvarkau.utils.PermissionUtils;
import lt.vilnius.tvarkau.utils.SharedPrefsManager;
import lt.vilnius.tvarkau.views.adapters.ProblemImagesPagerAdapter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import timber.log.Timber;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static lt.vilnius.tvarkau.ChooseReportTypeActivity.EXTRA_REPORT_TYPE;

@AutoComponent(modules = {LegacyApiModule.class, AppModule.class, SharedPreferencesModule.class})
@AutoInjector
@Singleton
public class NewProblemActivity extends BaseActivity {

    @Inject LegacyApiService legacyApiService;
    @Inject SharedPreferences myProblemsPreferences;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int GALLERY_REQUEST_CODE = 2;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 10;
    private static final int MAP_PERMISSION_REQUEST_CODE = 20;

    public static final int REQUEST_PLACE_PICKER = 11;
    public static final int REQUEST_PERSONAL_DATA = 12;
    public static final int REQUEST_CHOOSE_REPORT_TYPE = 13;

    public static final String[] MAP_PERMISSIONS = new String[]{ACCESS_FINE_LOCATION};
    private static final String[] LOCATION_PERMISSIONS = {WRITE_EXTERNAL_STORAGE, CAMERA, READ_EXTERNAL_STORAGE};

    public static final String PROBLEM_PREFERENCE_KEY = "problem";

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.report_problem_location)
    EditText reportProblemLocation;
    @BindView(R.id.problem_images_view_pager)
    ViewPager problemImagesViewPager;
    @BindView(R.id.problem_images_view_pager_indicator)
    CirclePageIndicator problemImagesViewPagerIndicator;
    @BindView(R.id.report_problem_type)
    EditText reportProblemType;
    @BindView(R.id.report_problem_description)
    EditText reportProblemDescription;
    @BindView(R.id.report_problem_location_wrapper)
    TextInputLayout reportProblemLocationWrapper;
    @BindView(R.id.report_problem_description_wrapper)
    TextInputLayout reportProblemDescriptionWrapper;
    @BindView(R.id.report_problem_type_wrapper)
    TextInputLayout reportProblemTypeWrapper;
    @BindView(R.id.report_problem_take_photo)
    ImageView reportProblemTakePhoto;

    @State
    File lastPhotoFile;
    @State
    LatLng locationCords;
    @State
    ArrayList<Uri> imagesURIs;
    @State
    String reportType;
    @State
    String address;

    private String photoFileName;
    private SharedPrefsManager prefsManager;
    private String reporterName;
    private String reporterDateOfBirth;
    private String reporterEmail;
    private String reporterPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_problem);

        ButterKnife.bind(this);

        DaggerNewProblemActivityComponent
            .builder()
            .appModule(new AppModule(this.getApplication()))
            .sharedPreferencesModule(new SharedPreferencesModule())
            .legacyApiModule(new LegacyApiModule())
            .build()
            .inject(this);

        prefsManager = SharedPrefsManager.getInstance(this);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close);

        initProblemImagesPager();

        imagesURIs = new ArrayList<>();
    }

    private void initProblemImagesPager() {
        problemImagesViewPager.setAdapter(new ProblemImagesPagerAdapter(this, null));
        problemImagesViewPager.setOffscreenPageLimit(3);
        problemImagesViewPagerIndicator.setViewPager(problemImagesViewPager);
        problemImagesViewPagerIndicator.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_send:
                sendProblem();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.new_problem_toolbar_menu, menu);

        return true;
    }

    public void sendProblem() {
        if (validateProblemInputs()) {

            ProgressDialog progressDialog = createProgressDialog();
            progressDialog.setCancelable(false);
            progressDialog.show();

            Observable<String[]> photoObservable;

            if (imagesURIs != null) {
                photoObservable = Observable.from(imagesURIs)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .map(uri -> Uri.fromFile(new File(ImageUtils.getPhotoPathFromUri(this, uri))))
                    .map(ImageUtils::convertToBase64EncodedString)
                    .toList()
                    .map(encodedPhotos -> {
                        if (encodedPhotos.size() > 0) {
                            String[] encodedPhotosArray = new String[encodedPhotos.size()];
                            encodedPhotos.toArray(encodedPhotosArray);
                            return encodedPhotosArray;
                        } else {
                            return null;
                        }
                    });
            } else {
                photoObservable = Observable.just(null);
            }

            Action1<ApiResponse<Integer>> onSuccess = apiResponse -> {
                if (apiResponse.getResult() != null) {
                    String newProblemId = apiResponse.getResult().toString();
                    myProblemsPreferences
                        .edit()
                        .putString(PROBLEM_PREFERENCE_KEY + newProblemId, newProblemId)
                        .apply();
                    EventBus.getDefault().post(new NewProblemAddedEvent());
                    progressDialog.dismiss();
                    if (reportProblemDescription.hasFocus()) {
                        KeyboardUtils.closeSoftKeyboard(this, reportProblemDescription);
                    }
                    Toast.makeText(this, R.string.problem_successfully_sent, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }
            };

            Action1<Throwable> onError = throwable -> {
                Timber.e(throwable);
                progressDialog.dismiss();
                Toast.makeText(getApplicationContext(), R.string.error_submitting_problem, Toast.LENGTH_SHORT).show();
            };

            if (prefsManager.isUserAnonymous()) {
                reporterName = null;
                reporterDateOfBirth = null;
                reporterEmail = null;
                reporterPhone = null;
            } else if (prefsManager.getUserProfile() != null) {
                reporterName = prefsManager.getUserProfile().getName();
                reporterDateOfBirth = FormatUtils.formatLocalDate(prefsManager.getUserProfile().getBirthday());
                reporterEmail = prefsManager.getUserProfile().getEmail();
                reporterPhone = prefsManager.getUserProfile().getMobilePhone();
            }

            photoObservable
                .subscribeOn(Schedulers.io())
                .flatMap(encodedPhotos ->
                    Observable.just(
                        new GetNewProblemParams.Builder()
                            .setSessionId(null)
                            .setDescription(reportProblemDescription.getText().toString())
                            .setType(reportType)
                            .setAddress(address)
                            .setLatitude(locationCords.latitude)
                            .setLongitude(locationCords.longitude)
                            .setPhoto(encodedPhotos)
                            .setEmail(reporterEmail)
                            .setPhone(reporterPhone)
                            .setNameOfReporter(reporterName)
                            .setDateOfBirth(reporterDateOfBirth)
                            .setMessageDescription(null)
                            .create())
                ).map(params -> new ApiRequest<>(ApiMethod.NEW_PROBLEM, params))
                .flatMap(request -> legacyApiService.postNewProblem(request))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    onSuccess,
                    onError
                );
        }
    }

    private ProgressDialog createProgressDialog() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.sending_problem));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        return progressDialog;
    }

    private boolean validateProblemInputs() {
        boolean addressIsValid = false;
        boolean descriptionIsValid = false;
        boolean problemTypeIsValid = false;

        if (address != null) {
            addressIsValid = true;
            reportProblemDescriptionWrapper.setError(null);
        } else {
            reportProblemLocationWrapper.setError(getText(R.string.error_problem_location_is_empty));
        }

        if (reportProblemDescription.getText() != null && reportProblemDescription.getText().length() > 0) {
            descriptionIsValid = true;
        } else {
            reportProblemDescriptionWrapper.setError(getText(R.string.error_problem_description_is_empty));
        }

        if (reportProblemType.getText().length() > 0) {
            problemTypeIsValid = true;
        } else {
            reportProblemTypeWrapper.setError(getText(R.string.error_problem_type_is_empty));
        }

        return addressIsValid && descriptionIsValid && problemTypeIsValid;
    }

    private boolean validateContacts() {
        if (reportType.equals("Transporto priemonių stovėjimo tvarkos pažeidimai") && (prefsManager.isUserAnonymous())) {

            new AlertDialog.Builder(this, R.style.MyDialogTheme)
                .setTitle(getString(R.string.personal_data_disabled))
                .setMessage(getString(R.string.share_personal_data))
                .setPositiveButton(getString(R.string.add), (dialog, which) -> {
                    if (prefsManager.isUserDetailsSaved()) {
                        fillReportTypeField();
                        Toast.makeText(getApplicationContext(), R.string.personal_data_sharing_enabled, Toast.LENGTH_SHORT).show();
                        prefsManager.changeUserAnonymityStatus(false);
                        dialog.dismiss();
                    } else {
                        Intent intent = new Intent(getApplicationContext(), ProfileEditActivity.class);
                        startActivityForResult(intent, REQUEST_PERSONAL_DATA);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> showReportTypePicker())
                .show();
            return false;
        } else {
            fillReportTypeField();
            return true;
        }
    }

    private void fillReportTypeField() {
        reportProblemTypeWrapper.setError(null);
        reportProblemType.setText(reportType);
    }

    private void openPhotoSelectorDialog() {

        AlertDialog.Builder imagePickerDialogBuilder = new AlertDialog.Builder(this, R.style.MyDialogTheme);

        View view = LayoutInflater.from(this).inflate(R.layout.image_picker_dialog, null);
        TextView cameraButton = ButterKnife.findById(view, R.id.camera_button);
        TextView galleryButton = ButterKnife.findById(view, R.id.gallery_button);

        imagePickerDialogBuilder
            .setTitle(this.getResources().getString(R.string.add_photos))
            .setView(view)
            .setPositiveButton(R.string.cancel, (dialog, whichButton) -> dialog.dismiss())
            .create();

        AlertDialog imagePickerDialog = imagePickerDialogBuilder.show();

        cameraButton.setOnClickListener(v -> {
            launchCamera();
            imagePickerDialog.dismiss();
        });

        galleryButton.setOnClickListener(v -> {
            openGallery();
            imagePickerDialog.dismiss();
        });
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        String timestamp = FormatUtils.formatLocalDateTimeToSeconds(LocalDateTime.now(ZoneOffset.UTC));
        photoFileName = "IMG_" + timestamp + ".jpg";
        intent.putExtra(MediaStore.EXTRA_OUTPUT, ImageUtils.getTakenPhotoFileUri(this, photoFileName));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, GALLERY_REQUEST_CODE);
        }
    }

    @OnClick(R.id.report_problem_take_photo)
    public void onTakePhotoClicked() {
        if (PermissionUtils.isAllPermissionsGranted(this, LOCATION_PERMISSIONS)) {
            openPhotoSelectorDialog();
        } else {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @OnClick(R.id.report_problem_type)
    public void onChooseProblemTypeClicked() {
        showReportTypePicker();
    }

    private void showReportTypePicker() {
        Intent intent = new Intent(this, ChooseReportTypeActivity.class);
        startActivityForResult(intent, REQUEST_CHOOSE_REPORT_TYPE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (PermissionUtils.isAllPermissionsGranted(this, LOCATION_PERMISSIONS)) {
                    openPhotoSelectorDialog();
                } else {
                    Toast.makeText(this, R.string.error_need_camera_and_storage_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            case MAP_PERMISSION_REQUEST_CODE:
                if (PermissionUtils.isAllPermissionsGranted(this, MAP_PERMISSIONS)) {
                    showPlacePicker(getCurrentFocus());
                } else {
                    Toast.makeText(this, R.string.error_need_location_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean isEditedByUser() {
        return reportProblemDescription.getText().length() > 0
            || reportProblemLocation.getText().length() > 0
            || (reportType != null && reportType.length() > 0)
            || (imagesURIs != null && imagesURIs.size() > 0);
    }

    @Override
    public void onBackPressed() {
        if (reportProblemDescription.hasFocus()) {
            KeyboardUtils.closeSoftKeyboard(this, reportProblemDescription);
        }
        if (isEditedByUser()) {
            new AlertDialog.Builder(this, R.style.MyDialogTheme)
                .setMessage(getString(R.string.discard_changes_title))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.discard_changes_positive, (dialog, whichButton) ->
                    NewProblemActivity.super.onBackPressed())
                .setNegativeButton(R.string.discard_changes_negative, null).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_CAPTURE:
                    Uri takenImageUri = ImageUtils.getTakenPhotoFileUri(this, photoFileName);
                    setImagesInViewPager(takenImageUri);
                    break;
                case GALLERY_REQUEST_CODE:
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            setImagesInViewPager(clipData.getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        Uri uri = data.getData();
                        setImagesInViewPager(uri);
                    }
                    break;
                case REQUEST_PLACE_PICKER:
                    Place place = PlacePicker.getPlace(this, data);
                    locationCords = place.getLatLng();

                    Geocoder geocoder = new Geocoder(this);

                    List<Address> addresses = null;
                    try {
                        addresses = geocoder.getFromLocation(locationCords.latitude, locationCords.longitude, 1);
                    } catch (IOException e) {
                        Timber.e(e);
                    }
                    if (addresses != null && addresses.size() > 0) {
                        if (addresses.get(0).getLocality() != null) {
                            address = addresses.get(0).getAddressLine(0);
                            reportProblemLocationWrapper.setError(null);
                            reportProblemLocation.setText(address);
                        }
                    } else {
                        // Mostly when Geocoder throws IOException
                        // backup solution which in not 100% reliable
                        String[] addressSlice = place.getAddress().toString().split(", ");
                        address = addressSlice[0];
                        reportProblemLocationWrapper.setError(null);
                        reportProblemLocation.setText(address);
                    }
                    break;
                case REQUEST_PERSONAL_DATA:
                    fillReportTypeField();
                    Toast.makeText(this, R.string.personal_data_added, Toast.LENGTH_SHORT).show();
                    Toast.makeText(this, R.string.personal_data_sharing_enabled, Toast.LENGTH_SHORT).show();
                    break;
                case REQUEST_CHOOSE_REPORT_TYPE:
                    if (data.hasExtra(EXTRA_REPORT_TYPE)) {
                        reportType = data.getStringExtra(EXTRA_REPORT_TYPE);
                        validateContacts();
                    }
                    break;
            }
        } else {
            switch (requestCode) {
                case REQUEST_IMAGE_CAPTURE:
                    Toast.makeText(this, R.string.photo_capture_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setImagesInViewPager(Uri uri) {
        imagesURIs.add(uri);
        String[] imagesPath = new String[imagesURIs.size()];
        for (int i = 0; i < imagesURIs.size(); i++) {
            String path = ImageUtils.getPhotoPathFromUri(this, imagesURIs.get(i));
            if (path != null) {
                imagesPath[i] = new File(path).toString();
            } else {
                imagesURIs.remove(uri);
                Toast.makeText(this, R.string.error_taking_image_from_server, Toast.LENGTH_LONG).show();
            }
        }
        if (imagesURIs.size() > 1) {
            problemImagesViewPagerIndicator.setVisibility(View.VISIBLE);
        }
        if (imagesURIs.size() > 0) {
            problemImagesViewPager.setAdapter(new ProblemImagesPagerAdapter<>(this, imagesPath));
        }
    }

    @OnClick(R.id.report_problem_location)
    public void onProblemLocationClicked(View view) {
        if ((PermissionUtils.isAllPermissionsGranted(this, MAP_PERMISSIONS))) {
            showPlacePicker(view);
        } else {
            requestPermissions(MAP_PERMISSIONS, MAP_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return false;
    }

    private void showPlacePicker(View view) {
        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
        try {
            Intent intent = builder.build(this);
            Bundle bundle = ActivityOptionsCompat.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight()).toBundle();
            ActivityCompat.startActivityForResult(this, intent, REQUEST_PLACE_PICKER, bundle);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            Timber.e(e);
            Snackbar.make(view, R.string.check_google_play_services, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.open, v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com" +
                        ".google.android.gms"));
                    startActivity(intent);
                })
                .setActionTextColor(ContextCompat.getColor(this, R.color.snackbar_action_text))
                .show();
        }
    }
}
