package onurgurbuz.drawline;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author manish *
 */
public class MapsActivity extends FragmentActivity {
    private GoogleMap googleMap;
    private ArrayList<LatLng> arrayPoints = null;
    private int i=0;
    PolylineOptions polylineOptions;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        arrayPoints = new ArrayList<LatLng>();
        SupportMapFragment fm = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        googleMap = fm.getMap();
        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
            // Haritada bir noktaya týklandýðýnda yapýlacaklar
            googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng point) {
                    arrayPoints.add(point);
                    MarkerOptions options = new MarkerOptions();
                    options.position(point);
                    googleMap.addMarker(options);
                    LatLng lastPoint = null,firstPoint=null;
                    lastPoint = arrayPoints.get(i);
                    if(i==0)
                        firstPoint=new LatLng(googleMap.getMyLocation().getLatitude(), googleMap.getMyLocation().getLongitude());
                    else
                        firstPoint=arrayPoints.get(i-1);
                    String url = getDirectionsUrl(firstPoint, lastPoint);
                    DownloadTask downloadTask = new DownloadTask();
                    downloadTask.execute(url);
                    i++;
                }

                public void onMapLongClick(LatLng point) {
                    googleMap.clear();
                    arrayPoints.clear();
                }

            });
        }
    }


    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        // Baþlangýç noktasýnýn enlem ve boylam bilgileri
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        // Hedef noktanýn enlem ve boylam bilgileri
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        // sensör aktifleþtiriliyor
        String sensor = "sensor=false";
        // webservice paratmetreleri yapýlandýrýlýyor
        String parameters = str_origin + "&" + str_dest + "&" + sensor;
        // Çýkýþ formatý
        String output = "json";
        // webservice url si yapýlandýrýlýyor
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
        return url;
    }

    /**
     * url den json data ya indirmeyi yapan method
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            // Url ile iletiþim kurmak için http baðlantýsý oluþturuluyor
            urlConnection = (HttpURLConnection) url.openConnection();
            // url ye baðlanýlýyor
            urlConnection.connect();
            // url den data okunuyor
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } catch (Exception e) {
            Log.d("URL indirilirken hata", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    // Geçen url lerden datalar alýnýyor
    private class DownloadTask extends AsyncTask<String, Void, String> {
        //thread de olmayan data indiriliyor
        @Override
        protected String doInBackground(String... url) {
            // webservice den data depolamak için
            String data = "";
            try {
                // webserviceden data alýnýyor
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Thread çalýþtýrýlýyor, after the execution of
        // doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            // JSON data parse edilir
            parserTask.execute(result);
        }
    }

    /**
     * JSON da ki yerleri parse eden sýnýf
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {
        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;
            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();
                // Data yý parse etme baþlýyor
                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        // Thread çalýþýr, parse etme iþleminden sonra.
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();
            // enlem boylamlar travers ediliyor
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();
                // i. enlem boylam getiriliyor
                List<HashMap<String, String>> path = result.get(i);
                // i. enlem boylam için bütün noktalar getiriliyor
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);
                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);
                    points.add(position);
                }
                // Çizgi ayarlarý için baþlangýç bitiþ noktalarý vs ayarlarý yapýlýyor
                lineOptions.addAll(points);
                lineOptions.width(2);
                lineOptions.color(Color.RED);
            }

            // Çizgi çiziliyor
            googleMap.addPolyline(lineOptions);
        }
    }
}