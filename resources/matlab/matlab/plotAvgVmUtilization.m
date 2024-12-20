function [] = plotAvgVmUtilization()

    plotGenericResult(2, 8, 'Average VM Utilization (%)', 'ALL_APPS', '');
    plotGenericResult(2, 8, 'Average VM Utilization for Health App (%)', 'HEALTH_APP', '');
    plotGenericResult(2, 8, 'Average VM Utilization for Infotainment App (%)', 'INFOTAINMENT_APP', '');
    plotGenericResult(2, 8, 'Average VM Utilization for Heavy Comp. App (%)', 'HEAVY_COMP_APP', '');

end