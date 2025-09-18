package sa.comptechco.nuxeo.common.filters.wrapers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class CustomRequestWrapper extends HttpServletRequestWrapper {

	public CustomRequestWrapper(HttpServletRequest request) {
		super(request);
	}
	public String getHeader(String name) {
		if (name.compareToIgnoreCase("X-NXDocumentProperties") == 0) {
			return "*";
		}
        String header = super.getHeader(name);
        return (header != null) ? header : super.getParameter(name); // Note: you can't use getParameterValues() here.
    }

    public Enumeration getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        names.addAll(Collections.list(super.getParameterNames()));
        names.add("X-NXDocumentProperties");
        
        return Collections.enumeration(names);
    }

}
