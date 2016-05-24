package lt.vilnius.tvarkau.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import butterknife.Bind;
import butterknife.ButterKnife;
import lt.vilnius.tvarkau.R;
import lt.vilnius.tvarkau.entity.Profile;
import lt.vilnius.tvarkau.utils.SharedPrefsManager;

import static android.app.Activity.RESULT_OK;


public class MyProfileFragment extends Fragment {

    private SharedPrefsManager prefsManager;

    @Bind(R.id.profile_name)
    EditText mProfileName;

    @Bind(R.id.profile_email)
    EditText mProfileEmail;

    @Bind(R.id.profile_telephone)
    EditText mProfileTelephone;


    public MyProfileFragment() {
    }

    public static MyProfileFragment getInstance() {
        return new MyProfileFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.my_profile, container, false);

        ButterKnife.bind(this, view);

        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        prefsManager = SharedPrefsManager.getInstance(getContext());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setUpUserProfile();
        mProfileTelephone.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.my_profile_menu, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            case R.id.profile_submit:
                saveUserProfile();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void saveUserProfile() {
        String name = mProfileName.getText().toString();
        String email = mProfileEmail.getText().toString();
        String phone = mProfileTelephone.getText().toString();

        Profile profile = new Profile(name, email, phone);

        prefsManager.saveUserDetails(profile);

        getActivity().setResult(RESULT_OK);

        Toast.makeText(getContext(), "User profile saved. " +
                "Implement sending logic.", Toast.LENGTH_SHORT).show();
    }

    private void setUpUserProfile() {
        if (!prefsManager.isUserAnonymous()) {
            Profile profile = Profile.returnProfile(getContext());

            mProfileName.setText(profile.getName());
            mProfileEmail.setText(profile.getEmail());
            mProfileTelephone.setText(profile.getMobilePhone());
        }
    }

    public boolean isEditedByUser() {
        if (mProfileName == null || mProfileEmail == null || mProfileTelephone == null) {
            return true;
        }

        String name = mProfileName.getText().toString();
        String email = mProfileEmail.getText().toString();
        String telephone = mProfileTelephone.getText().toString();

        if (name.length() == 0 && email.length() == 0 && telephone.length() == 0) {
            return true;
        }

        if (!prefsManager.isUserAnonymous()) {
            Profile oldProfile = prefsManager.getUserProfile();

            Profile newProfile = new Profile(name, email, telephone);

            return newProfile.equals(oldProfile);
        }

        return false;
    }

}
