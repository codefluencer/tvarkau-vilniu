package lt.vilnius.tvarkau.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import lt.vilnius.tvarkau.R;
import lt.vilnius.tvarkau.backend.ApiMethod;
import lt.vilnius.tvarkau.backend.ApiRequest;
import lt.vilnius.tvarkau.backend.ApiResponse;
import lt.vilnius.tvarkau.backend.GetProblemParams;
import lt.vilnius.tvarkau.backend.GetProblemsParams;
import lt.vilnius.tvarkau.entity.Problem;
import lt.vilnius.tvarkau.events_listeners.EndlessRecyclerViewScrollListener;
import lt.vilnius.tvarkau.events_listeners.NewProblemAddedEvent;
import lt.vilnius.tvarkau.utils.NetworkUtils;
import lt.vilnius.tvarkau.views.adapters.ProblemsListAdapter;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

public class ProblemsListFragment extends BaseFragment {

    @BindView(R.id.swipe_container) SwipeRefreshLayout swipeContainer;
    @BindView(R.id.problem_list) RecyclerView recyclerView;
    @BindView(R.id.my_problems_empty_view) View myProblemsEmptyView;
    @BindView(R.id.no_internet_view) View noInternetView;
    @BindView(R.id.server_not_responding_view) View serverNotRespondingView;
    @BindView(R.id.my_problems_import) LinearLayout myProblemsImport;

    private static final int PROBLEM_COUNT_LIMIT_PER_PAGE = 20;
    private static final String ALL_PROBLEM_LIST = "all_problem_list";
    private List<Problem> problemList;
    private ProblemsListAdapter adapter;
    private Unbinder unbinder;
    private CompositeSubscription subscriptions;
    private boolean isAllProblemList;
    private boolean shouldLoadMoreProblems;
    private boolean isLoading;

    public static ProblemsListFragment getAllProblemList() {
        ProblemsListFragment problemsListFragment = new ProblemsListFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ALL_PROBLEM_LIST, true);
        problemsListFragment.setArguments(arguments);
        return problemsListFragment;
    }

    public static ProblemsListFragment getMyProblemList() {
        ProblemsListFragment problemsListFragment = new ProblemsListFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ALL_PROBLEM_LIST, false);
        problemsListFragment.setArguments(arguments);
        return problemsListFragment;
    }

    public interface OnImportReportClickListener {
        void onImportReportClick();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isAllProblemList = getArguments().getBoolean(ALL_PROBLEM_LIST);
        }

        problemList = new ArrayList<>();
        subscriptions = new CompositeSubscription();
        shouldLoadMoreProblems = true;
        isLoading = false;

        EventBus.getDefault().register(this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.problem_list, container, false);

        unbinder = ButterKnife.bind(this, view);

        swipeContainer.setOnRefreshListener(this::reloadData);
        swipeContainer.setColorSchemeResources(R.color.colorAccent);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addOnScrollListener(new EndlessRecyclerViewScrollListener(linearLayoutManager) {
            @Override public void onLoadMore(int page, int totalItemsCount) {
                if (!isLoading) {
                    getData(page);
                }
            }
        });
        recyclerView.setOnTouchListener(
            (v, event) -> {
                if (isLoading) {
                    return true;
                } else {
                    return false;
                }
            }
        );

        adapter = new ProblemsListAdapter(getActivity(), problemList);
        recyclerView.setAdapter(adapter);

        myProblemsEmptyView.setVisibility(View.GONE);
        noInternetView.setVisibility(View.GONE);
        serverNotRespondingView.setVisibility(View.GONE);

        myProblemsImport.setOnClickListener(v -> {
            OnImportReportClickListener listener = (OnImportReportClickListener) (getActivity());
            listener.onImportReportClick();
        });

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (problemList.size() == 0 && shouldLoadMoreProblems) {
            getData(0);
        }
    }

    private void reloadData() {
        problemList.clear();
        getData(0);
    }

    private void setupView() {
        adapter.notifyDataSetChanged();

        if (!isAllProblemList) {
            if (problemList.size() == 0) {
                myProblemsEmptyView.setVisibility(View.VISIBLE);
                swipeContainer.setRefreshing(false);
            } else {
                myProblemsEmptyView.setVisibility(View.GONE);
            }
        }
    }

    private void getData(int page) {
        if (NetworkUtils.isNetworkConnected(getActivity())) {
            if (isLoading) {
                swipeContainer.setRefreshing(false);
                return;
            }

            if (isAllProblemList) {
                loadAllProblems(page);
            } else {
                loadMyProblems();
            }
        } else {
            swipeContainer.setRefreshing(false);
            myProblemsEmptyView.setVisibility(View.GONE);
            noInternetView.setVisibility(View.VISIBLE);
            problemList.clear();
            adapter.notifyDataSetChanged();
            adapter.hideLoader();
            showNoConnectionSnackbar();
        }
    }

    private void loadAllProblems(int page) {
        if (shouldLoadMoreProblems) {

            int startLoadingFromPage = page * PROBLEM_COUNT_LIMIT_PER_PAGE;

            GetProblemsParams params = new GetProblemsParams.Builder()
                .setStart(startLoadingFromPage)
                .setLimit(PROBLEM_COUNT_LIMIT_PER_PAGE)
                .setDescriptionFilter(null)
                .setTypeFilter(null)
                .setAddressFilter(null)
                .setReporterFilter(null)
                .setDateFilter(null)
                .setStatusFilter(null)
                .create();
            ApiRequest<GetProblemsParams> request = new ApiRequest<>(ApiMethod.GET_PROBLEMS, params);

            Action1<ApiResponse<List<Problem>>> onSuccess = apiResponse -> {
                shouldLoadMoreProblems = apiResponse.getResult().size() == PROBLEM_COUNT_LIMIT_PER_PAGE;
                isLoading = false;

                if (!shouldLoadMoreProblems) {
                    adapter.hideLoader();
                }

                if (apiResponse.getResult().size() > 0) {
                    problemList.addAll(apiResponse.getResult());
                    setupView();
                    if (getView() != null) {
                        swipeContainer.setRefreshing(false);
                    }
                }

                if (getView() != null) {
                    if (serverNotRespondingView.isShown()) {
                        serverNotRespondingView.setVisibility(View.GONE);
                    }
                    if (noInternetView.isShown()) {
                        noInternetView.setVisibility(View.GONE);
                    }
                }
            };

            Action1<Throwable> onError = throwable -> {
                Timber.e(throwable);
                if (getView() != null) {
                    serverNotRespondingView.setVisibility(View.VISIBLE);
                    adapter.hideLoader();
                    swipeContainer.setRefreshing(false);
                    isLoading = false;
                    shouldLoadMoreProblems = true;
                    showNoConnectionSnackbar();
                }
            };

            legacyApiService.getProblems(request)
                .doOnSubscribe(() -> isLoading = true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    onSuccess,
                    onError
                );
        }
    }

    private void loadMyProblems() {

        if (!shouldLoadMoreProblems) {
            swipeContainer.setRefreshing(false);
            return;
        }

        Action0 onSuccess = () -> {
            Collections.sort(problemList, (lhs, rhs) -> lhs.getEntryDate().isAfter(rhs.getEntryDate()) ? -1 : 1);
            setupView();
            swipeContainer.setRefreshing(false);
            adapter.hideLoader();
            shouldLoadMoreProblems = false;
            isLoading = false;
            if (serverNotRespondingView.isShown()) {
                serverNotRespondingView.setVisibility(View.GONE);
            }
            if (noInternetView.isShown()) {
                noInternetView.setVisibility(View.GONE);
            }
        };

        Action1<Throwable> onError = throwable -> {
            Timber.e(throwable);
            serverNotRespondingView.setVisibility(View.VISIBLE);
            adapter.hideLoader();
            swipeContainer.setRefreshing(false);
            isLoading = false;
            shouldLoadMoreProblems = true;
        };

        List<String> myProblemIds = new ArrayList<>();
        for (String key : myProblemsPreferences.getAll().keySet()) {
            myProblemIds.add(myProblemsPreferences.getString(key, ""));
        }

        Observable.from(myProblemIds)
            .doOnSubscribe(() -> isLoading = true)
            .flatMap(id -> Observable.zip(
                Observable.just(id),
                legacyApiService.getProblem(new ApiRequest<>(ApiMethod.GET_REPORT, new GetProblemParams(id))),
                Pair::new
            ))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                apiResponse -> {
                    String id = apiResponse.first;
                    Problem problem = apiResponse.second.getResult();
                    if (problem != null) {
                        problemList.add(problem);
                    } else {
                        myProblemsPreferences
                            .edit()
                            .remove(NewReportFragment.PROBLEM_PREFERENCE_KEY + id)
                            .apply();
                    }
                },
                onError,
                onSuccess
            );
    }

    private void showNoConnectionSnackbar() {
        Snackbar.make(getActivity().findViewById(R.id.coordinator_layout), R.string.no_connection, Snackbar
            .LENGTH_INDEFINITE)
            .setActionTextColor(ContextCompat.getColor(getContext(), R.color.snackbar_action_text))
            .setAction(R.string.try_again, v -> reloadData())
            .show();
    }

    @Subscribe
    public void onNewProblemAddedEvent(NewProblemAddedEvent event) {
        shouldLoadMoreProblems = true;
        isLoading = false;
        reloadData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (subscriptions != null) {
            subscriptions.unsubscribe();
            subscriptions = new CompositeSubscription();
        }
    }
}
