package org.olf.erm.usage.counter41.csv.cellprocessor;

import java.util.ArrayList;
import org.niso.schemas.counter.Identifier;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.util.CsvContext;

public class IdentifierProcessor extends CellProcessorAdaptor {

  private String identifier;

  public IdentifierProcessor(String identifier) {
    super();
    this.identifier = identifier;
  }

  @SuppressWarnings("unchecked")
  @Override
  public String execute(Object value, CsvContext csvContext) {
    return ((ArrayList<Identifier>) value)
        .stream()
        .filter(id -> identifier.equals(id.getType().value()))
        .findFirst()
        .map(Identifier::getValue)
        .orElse(null);
  }
}
