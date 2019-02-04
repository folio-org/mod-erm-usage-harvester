package org.olf.erm.usage.counter41;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class XMLGregorianCalendarSerializer extends StdSerializer<XMLGregorianCalendar> {

  private static final long serialVersionUID = 1L;

  protected XMLGregorianCalendarSerializer(Class<XMLGregorianCalendar> t) {
    super(t);
  }

  @Override
  public void serialize(XMLGregorianCalendar value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    GregorianCalendar gcal = value.toGregorianCalendar();
    if (value.getXMLSchemaType().equals(DatatypeConstants.DATE)) {
      gen.writeString(gcal.toZonedDateTime().toLocalDate().toString());
    } else {
      gen.writeString(
          gcal.toZonedDateTime()
              .toOffsetDateTime()
              .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ")));
    }
  }
}
