package org.olf.erm.usage.counter41.csv.cellprocessor;

import java.math.BigInteger;
import java.util.ArrayList;
import org.niso.schemas.counter.Metric;
import org.niso.schemas.counter.MetricType;
import org.niso.schemas.counter.PerformanceCounter;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.util.CsvContext;

public class ReportingPeriodProcessor extends CellProcessorAdaptor {

  private MetricType metricType;

  public ReportingPeriodProcessor(MetricType metricType) {
    super();
    this.metricType = metricType;
  }

  @SuppressWarnings("unchecked")
  @Override
  public BigInteger execute(Object value, CsvContext csvContext) {
    return ((ArrayList<Metric>) value)
        .stream()
        .flatMap(m -> m.getInstance().stream())
        .filter(pc -> pc.getMetricType().equals(metricType))
        .map(PerformanceCounter::getCount)
        .reduce((a, b) -> a.add(b))
        .orElse(null);
  }
}
