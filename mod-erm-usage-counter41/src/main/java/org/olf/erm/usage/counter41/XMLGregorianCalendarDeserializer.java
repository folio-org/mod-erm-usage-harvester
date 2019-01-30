package org.olf.erm.usage.counter41;

import java.io.IOException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class XMLGregorianCalendarDeserializer extends StdDeserializer<XMLGregorianCalendar> {

  private static final long serialVersionUID = 1L;

  protected XMLGregorianCalendarDeserializer(Class<XMLGregorianCalendar> vc) {
    super(vc);
  }

  @Override
  public XMLGregorianCalendar deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {

    try {
      return DatatypeFactory.newInstance().newXMLGregorianCalendar(p.getValueAsString());
    } catch (DatatypeConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return null;
  }
}
