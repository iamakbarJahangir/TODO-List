package com.example.todolist;

import android.os.Bundle;
import android.view.View; // Import the View class
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.widget.FrameLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private FrameLayout fragmentContainer;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        initViews();
        setupViewPager();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tab_layout);
        fragmentContainer = findViewById(R.id.fragment_container);

        // Create ViewPager and add it to the fragment container
        viewPager = new ViewPager(this);

        // ================== FIX START ==================
        // A ViewPager used with a FragmentPagerAdapter must have an ID.
        // Since we are creating this ViewPager programmatically and not in XML,
        // we must assign a unique ID to it here.
        viewPager.setId(View.generateViewId());
        // =================== FIX END ===================

        viewPager.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Clear fragment container and add ViewPager
        fragmentContainer.removeAllViews();
        fragmentContainer.addView(viewPager);
    }

    private void setupViewPager() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new LoginFragment(), "Sign In");
        adapter.addFragment(new SignUpFragment(), "Sign Up");

        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        // Set custom tab icons if they exist
        try {
            if (tabLayout.getTabAt(0) != null) {
                tabLayout.getTabAt(0).setIcon(R.drawable.ic_login);
            }
            if (tabLayout.getTabAt(1) != null) {
                tabLayout.getTabAt(1).setIcon(R.drawable.ic_person_add);
            }
        } catch (Exception e) {
            // Icons not found, continue without them
        }
    }

    // Method to switch tabs programmatically
    public void switchToTab(int position) {
        if (viewPager != null && position >= 0 && position < 2) {
            viewPager.setCurrentItem(position);
        }
    }

    // ViewPager Adapter
    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final java.util.List<Fragment> fragmentList = new java.util.ArrayList<>();
        private final java.util.List<String> fragmentTitleList = new java.util.ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitleList.get(position);
        }

        public void addFragment(Fragment fragment, String title) {
            fragmentList.add(fragment);
            fragmentTitleList.add(title);
        }
    }
}
