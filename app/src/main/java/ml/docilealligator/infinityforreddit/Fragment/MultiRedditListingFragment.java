package ml.docilealligator.infinityforreddit.Fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import javax.inject.Inject;
import javax.inject.Named;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import ml.docilealligator.infinityforreddit.Activity.BaseActivity;
import ml.docilealligator.infinityforreddit.Activity.CreateMultiRedditActivity;
import ml.docilealligator.infinityforreddit.Activity.SubscribedThingListingActivity;
import ml.docilealligator.infinityforreddit.Adapter.MultiRedditListingRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.CustomTheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.FragmentCommunicator;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.MultiReddit.MultiRedditViewModel;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.Utils.SharedPreferencesUtils;
import retrofit2.Retrofit;

public class MultiRedditListingFragment extends Fragment implements FragmentCommunicator {

    public static final String EXTRA_ACCOUNT_NAME = "EAN";
    public static final String EXTRA_ACCESS_TOKEN = "EAT";

    @BindView(R.id.swipe_refresh_layout_multi_reddit_listing_fragment)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.recycler_view_multi_reddit_listing_fragment)
    RecyclerView mRecyclerView;
    @BindView(R.id.fetch_multi_reddit_listing_info_linear_layout_multi_reddit_listing_fragment)
    LinearLayout mErrorLinearLayout;
    @BindView(R.id.fetch_multi_reddit_listing_info_image_view_multi_reddit_listing_fragment)
    ImageView mErrorImageView;
    @BindView(R.id.fetch_multi_reddit_listing_info_text_view_multi_reddit_listing_fragment)
    TextView mErrorTextView;
    @BindView(R.id.fab_multi_reddit_listing_fragment)
    FloatingActionButton fab;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    public MultiRedditViewModel mMultiRedditViewModel;
    private AppCompatActivity mActivity;
    private RequestManager mGlide;
    private LinearLayoutManager mLinearLayoutManager;

    public MultiRedditListingFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_multi_reddit_listing, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        ButterKnife.bind(this, rootView);

        applyTheme();

        if ((mActivity instanceof BaseActivity && ((BaseActivity) mActivity).isImmersiveInterface())) {
            mRecyclerView.setPadding(0, 0, 0, ((BaseActivity) mActivity).getNavBarHeight());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && mSharedPreferences.getBoolean(SharedPreferencesUtils.IMMERSIVE_INTERFACE_KEY, true)) {
            Resources resources = getResources();
            int navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
            if (navBarResourceId > 0) {
                mRecyclerView.setPadding(0, 0, 0, resources.getDimensionPixelSize(navBarResourceId));
            }
        }

        String accountName = getArguments().getString(EXTRA_ACCOUNT_NAME);
        String accessToken = getArguments().getString(EXTRA_ACCESS_TOKEN);

        mGlide = Glide.with(this);

        fab.setOnClickListener(view -> {
            Intent intent = new Intent(mActivity, CreateMultiRedditActivity.class);
            startActivity(intent);
        });

        mLinearLayoutManager = new LinearLayoutManager(mActivity);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        MultiRedditListingRecyclerViewAdapter adapter = new MultiRedditListingRecyclerViewAdapter(mActivity,
                mOauthRetrofit, mRedditDataRoomDatabase, mCustomThemeWrapper, accessToken, accountName);
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    fab.hide();
                } else {
                    fab.show();
                }
            }
        });
        new FastScrollerBuilder(mRecyclerView).build();

        mMultiRedditViewModel = new ViewModelProvider(this,
                new MultiRedditViewModel.Factory(mActivity.getApplication(), mRedditDataRoomDatabase, accountName))
                .get(MultiRedditViewModel.class);

        mMultiRedditViewModel.getAllMultiReddits().observe(this, subscribedUserData -> {
            if (subscribedUserData == null || subscribedUserData.size() == 0) {
                mRecyclerView.setVisibility(View.GONE);
                mErrorLinearLayout.setVisibility(View.VISIBLE);
                mGlide.load(R.drawable.error_image).into(mErrorImageView);
            } else {
                mErrorLinearLayout.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mGlide.clear(mErrorImageView);
            }
            adapter.setMultiReddits(subscribedUserData);
        });

        mMultiRedditViewModel.getAllFavoriteMultiReddits().observe(this, favoriteSubscribedUserData -> {
            if (favoriteSubscribedUserData != null && favoriteSubscribedUserData.size() > 0) {
                mErrorLinearLayout.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mGlide.clear(mErrorImageView);
            }
            adapter.setFavoriteMultiReddits(favoriteSubscribedUserData);
        });

        return rootView;
    }

    public void goBackToTop() {
        if (mLinearLayoutManager != null) {
            mLinearLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
    }

    @Override
    public void applyTheme() {
        if (mActivity instanceof SubscribedThingListingActivity) {
            mSwipeRefreshLayout.setOnRefreshListener(() -> ((SubscribedThingListingActivity) mActivity).loadSubscriptions(true));
            mSwipeRefreshLayout.setProgressBackgroundColorSchemeColor(mCustomThemeWrapper.getCircularProgressBarBackground());
            mSwipeRefreshLayout.setColorSchemeColors(mCustomThemeWrapper.getColorAccent());
        } else {
            mSwipeRefreshLayout.setEnabled(false);
        }

        mErrorTextView.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
        fab.setBackgroundTintList(ColorStateList.valueOf(mCustomThemeWrapper.getColorPrimaryLightTheme()));
        fab.setImageTintList(ColorStateList.valueOf(mCustomThemeWrapper.getFABIconColor()));
    }

    @Override
    public void stopRefreshProgressbar() {
        mSwipeRefreshLayout.setRefreshing(false);
    }
}