package com.sollyu.android.appenv.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.orhanobut.dialogplus.DialogPlus;
import com.orhanobut.dialogplus.ListHolder;
import com.orhanobut.dialogplus.OnItemClickListener;
import com.sollyu.android.appenv.BuildConfig;
import com.sollyu.android.appenv.R;
import com.sollyu.android.appenv.helper.AppEnvSharedPreferencesHelper;
import com.sollyu.android.appenv.helper.LibSuHelper;
import com.sollyu.android.appenv.helper.OtherHelper;
import com.sollyu.android.appenv.helper.RandomHelper;
import com.sollyu.android.appenv.helper.TokenHelper;
import com.sollyu.android.appenv.helper.XposedSharedPreferencesHelper;
import com.sollyu.android.appenv.module.AppInfo;

import org.xutils.view.annotation.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;
import qiu.niorgai.StatusBarCompat;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "AppEnv";

    private Handler uiHandler = new Handler();

    private NormalRecyclerViewAdapter _NormalRecyclerViewAdapter = null;
    private SwipeRefreshLayout        _SwipeRefreshLayout        = null;

    private ArrayList<ApplicationInfo> _DisplayApplicationInfo = new ArrayList<>();
    private List<ApplicationInfo>      applicationInfos        = null;

    private static final int READ_PHONE_STATE_REQUEST_CODE       = 227;
    private static final int START_DETAIL_ACTIVITY_REQUEST_CODE  = 733;
    private static final int START_SETTING_ACTIVITY_REQUEST_CODE = 21;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        StatusBarCompat.setStatusBarColor(getActivity(), getActivity().getResources().getColor(R.color.colorPrimaryDark));

        DrawerLayout          drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        RecyclerView _RecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        _RecyclerView.setLayoutManager(new LinearLayoutManager(this));
        _RecyclerView.setAdapter(_NormalRecyclerViewAdapter = new NormalRecyclerViewAdapter());

        _SwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.SwipeRefreshLayout);
        _SwipeRefreshLayout.setOnRefreshListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, READ_PHONE_STATE_REQUEST_CODE);
        } else {
            reportPhoneInfo();
        }

        MainActivity.this.onRefresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == READ_PHONE_STATE_REQUEST_CODE) {
            switch (grantResults[0]) {
                case PackageManager.PERMISSION_GRANTED:
                    reportPhoneInfo();
                    break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setIconified(true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                _NormalRecyclerViewAdapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                _NormalRecyclerViewAdapter.getFilter().filter(newText);
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_refresh) {
            MainActivity.this.onRefresh();
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.nav_donate) {
            OtherHelper.getInstance().openUrl(this, "https://mobilecodec.alipay.com/client_download.htm?qrcode=apynckrfcfi5atfy45");
        } else if (id == R.id.nav_score) {
            OtherHelper.getInstance().openMarket(this, getPackageName());
        } else if (id == R.id.action_settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), START_SETTING_ACTIVITY_REQUEST_CODE);
        } else if (id == R.id.action_cloud) {
            startActivity(new Intent(this, CloudActivity.class));
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return false;
    }

    @Override
    public synchronized void onRefresh() {
        AsyncTask<Object, Object, Object> asyncTask = new AsyncTask<Object, Object, Object>() {
            @Override
            protected void onPreExecute() {
                _SwipeRefreshLayout.setRefreshing(true);
                _NormalRecyclerViewAdapter.notifyItemRangeRemoved(0, _DisplayApplicationInfo.size());
                _DisplayApplicationInfo.clear();
            }

            @Override
            protected Object doInBackground(Object... params) {
                ArrayList<String> ignorePackageName = new ArrayList<>();
                ignorePackageName.add("android");
                ignorePackageName.add(BuildConfig.APPLICATION_ID);
                ignorePackageName.add("de.robv.android.xposed.installer");

                int hasConfigIndex = 0;

                applicationInfos = MainActivity.this.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
                Collections.sort(applicationInfos, new ApplicationInfo.DisplayNameComparator(MainActivity.this.getPackageManager()));

                for (ApplicationInfo applicationInfo : applicationInfos) {
                    if (ignorePackageName.contains(applicationInfo.packageName)) {
                        continue;
                    }

                    if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("show_system_app", false) || OtherHelper.getInstance().isUserAppllication(applicationInfo)) {
                        if (XposedSharedPreferencesHelper.getInstance().get(applicationInfo.packageName) != null) {
                            _DisplayApplicationInfo.add(hasConfigIndex++, applicationInfo);
                        } else {
                            _DisplayApplicationInfo.add(applicationInfo);
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                _NormalRecyclerViewAdapter.setmOriginalValues(_DisplayApplicationInfo);
                _NormalRecyclerViewAdapter.notifyDataSetChanged();
                _SwipeRefreshLayout.setRefreshing(false);
            }
        };
        asyncTask.execute();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == START_DETAIL_ACTIVITY_REQUEST_CODE && resultCode == 1) {
            onRefresh();
        }
        if (requestCode == START_SETTING_ACTIVITY_REQUEST_CODE && resultCode == 1) {
            onRefresh();
        }
    }

    private void reportPhoneInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!AppEnvSharedPreferencesHelper.getInstance().isReportPhone())
                    AppEnvSharedPreferencesHelper.getInstance().setReportPhone(TokenHelper.getInstance().devices(MainActivity.this).getRet() == 200);
            }
        }).start();
    }

    @SuppressWarnings("unused")
    @Event(R.id.action_hook_all)
    public void onMenuHookAll(MenuItem item) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = XposedSharedPreferencesHelper.KEY_ALL;

        Intent intent = new Intent(MainActivity.this, DetailActivity.class);
        intent.putExtra("applicationInfo", applicationInfo);
        MainActivity.this.startActivityForResult(intent, 0);
    }

    @SuppressWarnings("unused")
    @Event(R.id.action_hook_user)
    public void onMenuHookUser(MenuItem item) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = XposedSharedPreferencesHelper.KEY_USER;

        Intent intent = new Intent(MainActivity.this, DetailActivity.class);
        intent.putExtra("applicationInfo", applicationInfo);
        MainActivity.this.startActivityForResult(intent, 0);
    }

    private class NormalRecyclerViewAdapter extends RecyclerView.Adapter<NormalRecyclerViewAdapter.NormalTextViewHolder> implements Filterable {
        private ArrayList<ApplicationInfo> mOriginalValues = new ArrayList<>();

        @Override
        public NormalRecyclerViewAdapter.NormalTextViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new NormalTextViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.content_listview, parent, false));
        }

        @Override
        public void onBindViewHolder(final NormalRecyclerViewAdapter.NormalTextViewHolder holder, int position) {
            holder.applicationInfo = _DisplayApplicationInfo.get(position);
            ;
            holder.textView1.setText(holder.applicationInfo.loadLabel(getPackageManager()));
            holder.textView2.setText(holder.applicationInfo.packageName);
            holder.imageView.setImageDrawable(holder.applicationInfo.loadIcon(getPackageManager()));

            AppInfo appInfo = XposedSharedPreferencesHelper.getInstance().get(holder.applicationInfo.packageName);
            Log.d(TAG, "onBindViewHolder: " + JSON.toJSONString(appInfo));
            holder.textView1.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), appInfo != null ? R.color.bootstrap_brand_success : android.R.color.primary_text_light));

            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    {
                        List<HashMap<String, Object>> mapList = new ArrayList<>();

                        HashMap<String, Object> openItem = new HashMap<>();
                        openItem.put("title", getString(R.string.open));
                        openItem.put("icon", R.drawable.ic_crop_landscape);
                        mapList.add(openItem);

                        openItem = new HashMap<>();
                        openItem.put("title", getString(R.string.random));
                        openItem.put("icon", R.drawable.ic_auto_fix);
                        mapList.add(openItem);

                        openItem = new HashMap<>();
                        openItem.put("title", getString(R.string.detail));
                        openItem.put("icon", R.drawable.ic_info_outline);
                        mapList.add(openItem);

                        openItem = new HashMap<>();
                        openItem.put("title", getString(R.string.delete));
                        openItem.put("icon", R.drawable.ic_delete);
                        mapList.add(openItem);

                        SimpleAdapter simpleAdapter = new SimpleAdapter(holder.itemView.getContext(), mapList, R.layout.dialog_plus_content, new String[]{"title", "icon"}, new int[]{R.id.text_view, R.id.image_view});

                        DialogPlus dialogPlus = DialogPlus.newDialog(holder.itemView.getContext()).setExpanded(false).setContentHolder(new ListHolder()).setHeader(R.layout.dialog_plus_header).setAdapter(simpleAdapter).setOnItemClickListener(new OnItemClickListener() {
                            @Override
                            public void onItemClick(DialogPlus dialog, Object item, final View view, int position1) {
                                dialog.dismiss();
                                switch (position1) {
                                    case 0: // Open
                                        holder.itemView.performClick();
                                        break;
                                    case 1: // Random
                                        XposedSharedPreferencesHelper.getInstance().set(holder.textView2.getText().toString(), RandomHelper.getInstance().randomAll());
                                        uiHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                Snackbar.make(view, R.string.random_success, Snackbar.LENGTH_LONG).setAction(R.string.force_stop, new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        LibSuHelper.getInstance().addCommand("am force-stop " + holder.applicationInfo.packageName, 0, new Shell.OnCommandResultListener() {
                                                            @Override
                                                            public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                                                                if (exitCode != 0)
                                                                    Snackbar.make(findViewById(R.id.content_main), getString(R.string.force_stop_error) + exitCode, Snackbar.LENGTH_LONG).show();
                                                                else
                                                                    Snackbar.make(findViewById(R.id.content_main), R.string.force_stop_success, Snackbar.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }
                                                }).show();
                                            }
                                        }, 250);

                                        onRefresh();
                                        break;
                                    case 2: // Detail
                                        OtherHelper.getInstance().openAppDetails(holder.itemView.getContext(), holder.textView2.getText().toString());
                                        break;
                                    case 3: // Delete
                                        XposedSharedPreferencesHelper.getInstance().remove(holder.textView2.getText().toString());
                                        MainActivity.this.onRefresh();
                                        break;
                                }
                            }
                        }).create();

                        ((TextView) dialogPlus.getHeaderView().findViewById(R.id.text_view1)).setText(holder.textView1.getText());
                        ((TextView) dialogPlus.getHeaderView().findViewById(R.id.text_view2)).setText(holder.textView2.getText());

                        dialogPlus.show();
                        return true;
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return _DisplayApplicationInfo.size();
        }

        public void setmOriginalValues(ArrayList<ApplicationInfo> mOriginalValues) {
            this.mOriginalValues = mOriginalValues;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();

                    if (constraint == null || constraint.length() == 0) {
                        ArrayList<ApplicationInfo> applicationInfoArrayList = new ArrayList<>(mOriginalValues);
                        filterResults.values = applicationInfoArrayList;
                        filterResults.count = applicationInfoArrayList.size();
                        return filterResults;
                    }

                    String                           prefixString = constraint.toString().toLowerCase();
                    final ArrayList<ApplicationInfo> values       = mOriginalValues;
                    final int                        count        = mOriginalValues.size();

                    final ArrayList<ApplicationInfo> newValues = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        final ApplicationInfo applicationInfo = values.get(i);

                        if (applicationInfo.packageName.toLowerCase().contains(prefixString) || applicationInfo.loadLabel(getPackageManager()).toString().toLowerCase().contains(prefixString))
                            newValues.add(applicationInfo);
                    }

                    filterResults.values = newValues;
                    filterResults.count = newValues.size();

                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    _DisplayApplicationInfo = (ArrayList<ApplicationInfo>) results.values;
                    notifyDataSetChanged();
                }
            };
        }

        class NormalTextViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            private TextView        textView1;
            private TextView        textView2;
            private ImageView       imageView;
            private ApplicationInfo applicationInfo;

            NormalTextViewHolder(View itemView) {
                super(itemView);

                textView1 = (TextView) itemView.findViewById(R.id.text1);
                textView2 = (TextView) itemView.findViewById(R.id.text2);
                imageView = (ImageView) itemView.findViewById(R.id.image_view);

                itemView.setOnClickListener(this);

            }

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra("applicationInfo", applicationInfo);
                MainActivity.this.startActivityForResult(intent, START_DETAIL_ACTIVITY_REQUEST_CODE);
            }
        }
    }
}
