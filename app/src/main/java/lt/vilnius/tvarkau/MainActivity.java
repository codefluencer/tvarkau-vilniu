package lt.vilnius.tvarkau;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import lt.vilnius.tvarkau.fragments.ProblemsListFragment;
import lt.vilnius.tvarkau.fragments.ProblemsMapFragment;

/**
 * An activity representing a list of Problems. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ProblemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class MainActivity extends AppCompatActivity implements Drawer.OnDrawerItemClickListener, AccountHeader.OnAccountHeaderListener {

    @Bind(R.id.toolbar)
    Toolbar toolbar;


    Drawer drawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        setFrameFragment(R.id.main_nav_problems_list);
        setNavigationDrawer(savedInstanceState);
    }

    public void setNavigationDrawer(Bundle savedInstanceState) {
        AccountHeader headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.header)
                .addProfiles(
                        new ProfileDrawerItem().withName("Mike Penz").withEmail("mikepenz@gmail.com").withIcon(getResources().getDrawable(R.drawable.profile_photo))
                )
                .withOnAccountHeaderListener(this)
                .build();


        drawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withAccountHeader(headerResult)
                .withSavedInstance(savedInstanceState)
                .withOnDrawerItemClickListener(this)
                .withShowDrawerOnFirstLaunch(true)
                .inflateMenu(R.menu.main_navigation_menu)
                .build();
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    protected void setFrameFragment(int itemId) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        Fragment fragment;

        switch (itemId) {
            case R.id.main_naw_problems_map:
                fragment = ProblemsMapFragment.getInstance();
                break;
            case R.id.main_nav_my_problems_list:
            case R.id.main_nav_problems_list:
                fragment = ProblemsListFragment.getInstance();
                break;
            default:
                throw new IllegalArgumentException("Can't find fragment for given navigation item id " + itemId);
        }

        ft.replace(R.id.mainFrameLayout, fragment);

        ft.commit();
    }

    @OnClick(R.id.fab)
    public void onNewProblemClicked(View view) {
        startActivityForResult(new Intent(this, NewProblemActivity.class), 0);
    }

    @Override
    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
        setFrameFragment(drawerItem.getIdentifier());
        drawer.closeDrawer();
        return true;
    }

    @Override
    public boolean onProfileChanged(View view, IProfile profile, boolean current) {
        return false;
    }
}
