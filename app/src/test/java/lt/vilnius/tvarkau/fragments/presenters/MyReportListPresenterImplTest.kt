package lt.vilnius.tvarkau.fragments.presenters

import com.nhaarman.mockito_kotlin.*
import io.reactivex.Single.error
import io.reactivex.Single.just
import io.reactivex.schedulers.Schedulers
import lt.vilnius.tvarkau.entity.Problem
import lt.vilnius.tvarkau.fragments.interactors.ReportListInteractor
import lt.vilnius.tvarkau.fragments.presenters.ConnectivityProviderImpl.NetworkConnectivityError
import lt.vilnius.tvarkau.fragments.views.ReportListView
import org.junit.Test

/**
 * @author Martynas Jurkus
 */
class MyReportListPresenterImplTest {

    val interactor = mock<ReportListInteractor>()
    val view = mock<ReportListView>()
    val connectivityProvider = mock<ConnectivityProvider> {
        on { ensureConnected() } doReturn just(true)
    }

    val fixture = MyReportListPresenterImpl(
            interactor,
            Schedulers.trampoline(),
            view,
            connectivityProvider
    )

    val reports = listOf(Problem(id = "1"), Problem(id = "2"))

    @Test
    fun loadReports_success() {
        whenever(interactor.getProblems(any())).thenReturn(just(reports))

        fixture.getReportsForPage(1)

        verify(view).showProgress()
        verify(view).hideProgress()
        verify(view).hideLoader()
        verify(view).onReportsLoaded(reports)
    }

    @Test
    fun loadReports_emptyList_showEmptyState() {
        whenever(interactor.getProblems(any())).thenReturn(just(emptyList()))

        fixture.getReportsForPage(1)

        verify(view).showEmptyState()
        verify(view, never()).onReportsLoaded(any())
    }

    @Test
    fun loadReports_noNetwork_showNetworkError() {
        whenever(connectivityProvider.ensureConnected()).thenReturn(error(NetworkConnectivityError()))

        fixture.getReportsForPage(1)

        verify(view).showNetworkError(1)
        verify(interactor, never()).getProblems(any())
    }

    @Test
    fun loadReports_otherError_showError() {
        val exception = RuntimeException("Some error")
        whenever(interactor.getProblems(1)).thenReturn(error(exception))

        fixture.getReportsForPage(1)

        verify(view).showProgress()
        verify(view).hideProgress()
        verify(view).showError(exception)
    }
}