package rocks.inspectit.agent.java.sensor.method.remote.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.Test;

import io.opentracing.References;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import rocks.inspectit.agent.java.config.impl.RegisteredSensorConfig;
import rocks.inspectit.agent.java.tracing.core.adapter.ClientRequestAdapter;
import rocks.inspectit.agent.java.tracing.core.adapter.ResponseAdapter;
import rocks.inspectit.shared.all.testbase.TestBase;
import rocks.inspectit.shared.all.tracing.constants.ExtraTags;
import rocks.inspectit.shared.all.tracing.data.PropagationType;

/**
 * @author Ivan Senic
 *
 */
@SuppressWarnings("PMD")
public class UrlConnectionSensorTest extends TestBase {

	@InjectMocks
	UrlConnectionSensor sensor;

	@Mock
	RegisteredSensorConfig rsc;

	@Mock
	HttpURLConnection urlConnection;

	public static class GetClientRequestAdapter extends UrlConnectionSensorTest {

		public void properties() {
			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			assertThat(adapter.getPropagationType(), is(PropagationType.HTTP));
			assertThat(adapter.getReferenceType(), is(References.CHILD_OF));
			assertThat(adapter.getFormat(), is(Format.Builtin.HTTP_HEADERS));
			verifyZeroInteractions(rsc);
		}

		@Test
		public void spanStarting() {
			when(urlConnection.getRequestProperty(anyString())).thenReturn(null);

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			assertThat(adapter.startClientSpan(), is(true));
		}

		@Test
		public void spanStartingContainsHeader() {
			when(urlConnection.getRequestProperty(anyString())).thenReturn("bla bla");

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			assertThat(adapter.startClientSpan(), is(false));
		}

		@Test
		public void spanStartingAlreadyConnected() throws Exception {
			doThrow(IllegalStateException.class).when(urlConnection).setRequestMethod(anyString());

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			assertThat(adapter.startClientSpan(), is(false));
		}

		@Test
		public void url() throws MalformedURLException {
			String uri = "http://localhost";
			when(urlConnection.getURL()).thenReturn(new URL(uri));

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			Map<String, String> tags = adapter.getTags();
			assertThat(tags.size(), is(1));
			assertThat(tags, hasEntry(Tags.HTTP_URL.getKey(), uri));
			verifyZeroInteractions(rsc);
		}

		@Test
		public void urlNull() {
			when(urlConnection.getURL()).thenReturn(null);

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			Map<String, String> tags = adapter.getTags();
			assertThat(tags.size(), is(0));
			verifyZeroInteractions(rsc);
		}

		@Test
		public void method() throws MalformedURLException {
			String method = "GET";
			when(urlConnection.getRequestMethod()).thenReturn(method);

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);

			Map<String, String> tags = adapter.getTags();
			assertThat(tags.size(), is(1));
			assertThat(tags, hasEntry(Tags.HTTP_METHOD.getKey(), method));
			verifyZeroInteractions(rsc);
		}

		@Test
		public void baggageInjection() {
			String key = "key";
			String value = "value";

			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(urlConnection, null, rsc);
			adapter.getCarrier().put(key, value);

			verify(urlConnection).setRequestProperty(key, value);
			verifyZeroInteractions(rsc);
		}

		@Test
		public void notUrlConnection() throws IOException {
			ClientRequestAdapter<TextMap> adapter = sensor.getClientRequestAdapter(new Object(), null, rsc);

			assertThat(adapter, is(nullValue()));
		}
	}

	public static class GetClientResponseAdapter extends UrlConnectionSensorTest {

		@Mock
		Object result;

		@Test
		public void status() throws IOException {
			int status = 200;
			when(urlConnection.getResponseCode()).thenReturn(status);
			when(rsc.getTargetMethodName()).thenReturn("getInputStream");

			ResponseAdapter adapter = sensor.getClientResponseAdapter(urlConnection, null, result, false, rsc);

			Map<String, String> tags = adapter.getTags();
			assertThat(tags.size(), is(1));
			assertThat(tags, hasEntry(Tags.HTTP_STATUS.getKey(), String.valueOf(status)));
			verify(rsc).getTargetMethodName();
			verifyNoMoreInteractions(rsc);
			verifyZeroInteractions(result);
		}

		@Test
		public void statusIOException() throws IOException {
			when(urlConnection.getResponseCode()).thenThrow(new IOException());
			when(rsc.getTargetMethodName()).thenReturn("getInputStream");

			ResponseAdapter adapter = sensor.getClientResponseAdapter(urlConnection, null, result, false, rsc);

			Map<String, String> tags = adapter.getTags();
			assertThat(tags.size(), is(0));
			verify(rsc).getTargetMethodName();
			verifyNoMoreInteractions(rsc);
			verifyZeroInteractions(result);
		}

		@Test
		public void notUrlConnection() throws IOException {
			when(rsc.getTargetMethodName()).thenReturn("getInputStream");

			ResponseAdapter adapter = sensor.getClientResponseAdapter(new Object(), null, result, false, rsc);

			assertThat(adapter, is(nullValue()));
			verifyZeroInteractions(result, rsc);
		}

		@Test
		public void notInputStreamMethod() throws IOException {
			when(rsc.getTargetMethodName()).thenReturn("connect");

			ResponseAdapter adapter = sensor.getClientResponseAdapter(urlConnection, null, result, false, rsc);

			assertThat(adapter, is(nullValue()));
			verify(rsc).getTargetMethodName();
			verifyNoMoreInteractions(rsc);
			verifyZeroInteractions(result);
		}

		@Test
		public void exception() throws IOException {
			int status = 200;
			when(urlConnection.getResponseCode()).thenReturn(status);
			when(rsc.getTargetMethodName()).thenReturn("getInputStream");

			ResponseAdapter adapter = sensor.getClientResponseAdapter(urlConnection, null, new NullPointerException(), true, rsc);

			Map<String, String> tags = adapter.getTags();
			assertThat(tags.size(), is(3));
			assertThat(tags, hasEntry(Tags.HTTP_STATUS.getKey(), String.valueOf(status)));
			assertThat(tags, hasEntry(Tags.ERROR.getKey(), String.valueOf(true)));
			assertThat(tags, hasEntry(ExtraTags.THROWABLE_TYPE, NullPointerException.class.getSimpleName()));
			verify(rsc).getTargetMethodName();
			verifyNoMoreInteractions(rsc);
		}

	}

}