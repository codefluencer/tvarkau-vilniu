package lt.vilnius.tvarkau.api

import lt.vilnius.tvarkau.entity.City
import lt.vilnius.tvarkau.entity.Report
import lt.vilnius.tvarkau.entity.ReportStatus
import lt.vilnius.tvarkau.entity.ReportType
import lt.vilnius.tvarkau.entity.User

class CitiesResponse(val cities: List<City>)

class ReportsResponse(val reports: List<Report>)

class ReportTypeResponse(val reportTypes: List<ReportType>)

class ReportStatusesResponse(val reportStatuses: List<ReportStatus>)

class ReportResponse(val report: Report)

class UserResponse(val user : User)
