package com.example.bmap_debug1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.Poi;
import com.baidu.location.PoiRegion;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.CircleOptions;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Stroke;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiNearbySearchOption;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.navisdk.adapter.BNRoutePlanNode;
import com.baidu.navisdk.adapter.BNaviCommonParams;
import com.baidu.navisdk.adapter.BaiduNaviManagerFactory;
import com.baidu.navisdk.adapter.IBNRoutePlanManager;
import com.baidu.navisdk.adapter.IBaiduNaviManager;
import com.example.bmap_debug1.service.LocationService;
import com.example.bmap_debug1.service.PoiOverlay;
import com.example.bmap_debug1.service.PoiSearchService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public MapView mMapView;
    public BaiduMap mMap;
    private LocationService locationService;
    private TextView LocationResult;
    private Button searchPOI;
    private Button searchNext;
    private Button startNavigation;
    private int locTimes = 0;//定位次数，用于控制地图更新动作（仅第一次调整中心和比例）
    private PoiSearchService poiSearchService;
    private EditText mEditRadius;//半径输入框
    private RelativeLayout mPoiDetailView;
    private TextView mPoiResult;
    private List<PoiInfo> mAllPoi;
    private LatLng ll;//地理信息数据结构，初始值为当前地址，用于定时时更新地图（中心、范围标记）以及检索时充当检索中心点;点击地图后，更新为所点击的位置，用于指定位置检索以及路径规划和导航
    private LatLng ll_start;//导航的起点,固定为当前位置
    private int radius;//半径
    private int num = 0;//检索分页数量
    private BitmapDescriptor mbitmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_marka);//点击标记图标，指示用户意向地址


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        setContentView(R.layout.main_activity1);//测试新的布局

        // -----------demo view config ------------
        LocationResult = (TextView) findViewById(R.id.textView);//定位结果展示栏
        mEditRadius = (EditText) findViewById(R.id.edit_radius);//半径输入栏
        searchPOI = (Button) findViewById(R.id.search_poi);//检索按钮
        searchNext = (Button) findViewById(R.id.search_next);//下一组按钮
        startNavigation = (Button) findViewById(R.id.start_navi);
        mMapView = (MapView) findViewById(R.id.bmapView);//地图
        mMap = mMapView.getMap();//获取地图控件对象
        mMap.setMyLocationEnabled(true);//开启定位地图图层
        mPoiDetailView = (RelativeLayout) findViewById(R.id.poi_detail);
        mPoiResult = (TextView) findViewById(R.id.poi_result);//POI检索信息展示

        //获取locationservice实例
        locationService = ((Map) getApplication()).locationService;
        //注册定位监听
        locationService.registerListener(mListener);
        //设置定位参数
        locationService.setLocationOption(locationService.getDefaultLocationClientOption());

        //创建POI检索实例并注册监听
        poiSearchService = new PoiSearchService(poiListener);
        //注册地图点击事件的监听响应
        initClickListener();
    }

    @Override
    protected void onStart() {
        super.onStart();
        //开始定位
        locationService.start();// 定位SDK
        locTimes = 0;//定位次数置零
    }

    @Override
    protected void onResume() {
        super.onResume();

        //开始检索+上一组检索
        searchPOI.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                radius = Integer.parseInt(mEditRadius.getText().toString());//获取半径
                if (searchPOI.getText().toString().equals(getString(R.string.startsearch))) {
                    //开始检索
                    num = 0;
                    searchNearby(radius, num);
                } else if (num > 1) {
                    num--;
                    searchNearby(radius, num);
                } else if (num == 1) {
                    num--;
                    searchNearby(radius, num);
                    searchPOI.setText(getString(R.string.startsearch));
                }
            }
        });

        //下一组检索
        searchNext.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //检索下一组
                radius = Integer.parseInt(mEditRadius.getText().toString());//获取半径
                num++;
                searchNearby(radius, num);
                //检索按钮变为“上一组”
                searchPOI.setText(getString(R.string.previous));
            }
        });

        //启动导航
        startNavigation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //初始化导航服务
                initNavi();
                //开始算路并唤起导航
                startPlanAndNavi(ll_start, ll);
            }
        });

        //重新输入半径，重置检索
        mEditRadius.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                searchPOI.setText(getString(R.string.startsearch));
            }
        });
    }

    @Override
    protected void onStop() {
        // TODO Auto-generated method stub
        locationService.unregisterListener(mListener); //注销掉监听
        locationService.stop(); //停止定位服务
        super.onStop();
    }


    //实现定位监听
    private BDAbstractLocationListener mListener = new BDAbstractLocationListener() {

        @Override
        public void onReceiveLocation(BDLocation location) {
            // map view 销毁后不在处理新接收的位置
            if (location == null || mMapView == null) {
                return;
            }

            //仅当第一次定位，调整中心和比例
            locTimes++;
            if (locTimes == 1) {
                //构造地理坐标数据
                ll = new LatLng(location.getLatitude(),
                        location.getLongitude());

                ll_start = ll;//设置导航起点

                //（以动画方式）改变地图状态（中心、倍数等）
                MapStatus.Builder builder = new MapStatus.Builder();
                builder.target(ll).zoom(17.0f);
                mMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            }

            //构造定位数据
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(location.getDirection())
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .build();
            //设置定位数据，设置并显示定位蓝点
//            BitmapDescriptor BitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.location_marker);//自定义图标
            MyLocationConfiguration mLocationConfig = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
            mMap.setMyLocationConfiguration(mLocationConfig);
            mMap.setMyLocationData(locData);//显示定位蓝点、

            //检测定位结果
            StringBuffer sb = new StringBuffer(256);
            sb.append("结果：\n");
            sb.append("lat:");
            sb.append(location.getLatitude() + "\n");
            sb.append("lon:");
            sb.append(location.getLongitude() + "\n");
            sb.append("Radius:");
            sb.append(location.getRadius() + "\n");
            sb.append("Direc:");
            sb.append(location.getDirection() + "\n");
            sb.append("locTimes:");
            sb.append(locTimes + "\n");
            sb.append("Code:");
            sb.append(location.getLocType() + "\n");
            //检测POI获取结果
            if (location.getPoiList() == null || location.getPoiList().isEmpty()) {
                sb.append("POI为空！！！！！！！！！！！！！！！！！！");
            } else {
                for (int i = 0; i < location.getPoiList().size(); i++) {
                    Poi poi = (Poi) location.getPoiList().get(i);
                    sb.append("poiName:");
                    sb.append(poi.getName() + ", ");
                    sb.append("poiTag:");
                    sb.append(poi.getTags() + "\n");
                }
            }
            if (location.getPoiRegion() == null) {
                sb.append("Region为空！！！！！！！！！！！！！！！\n");
            } else {
                sb.append("PoiRegion ");// 返回定位位置相对poi的位置关系，仅在开发者设置需要POI信息时才会返回，在网络不通或无法获取时有可能返回null
                PoiRegion poiRegion = location.getPoiRegion();
                sb.append("DerectionDesc:"); // 获取POIREGION的位置关系
                sb.append(poiRegion.getDerectionDesc() + "; ");
                sb.append("Name:"); // 获取POIREGION的名字字符串
                sb.append(poiRegion.getName() + "; ");
                sb.append("Tags:"); // 获取POIREGION的类型
                sb.append(poiRegion.getTags() + "; ");
                sb.append("\nSDK版本: ");
            }

            //更新textview
//            updateMap(LocationResult, sb.toString());
        }
    };

    //实现POI检索监听
    private OnGetPoiSearchResultListener poiListener = new OnGetPoiSearchResultListener() {
        @Override
        public void onGetPoiResult(final PoiResult result) {

            if (result == null || result.error == SearchResult.ERRORNO.RESULT_NOT_FOUND) {
                Toast.makeText(MainActivity.this, "以下再无信息", Toast.LENGTH_LONG).show();
                return;
            }

            StringBuffer sb1 = new StringBuffer(256);
            //POI检索结果地图标记形式
            mMap.clear();//清空地图标记
            if (result.error == SearchResult.ERRORNO.NO_ERROR) {
                //监听 View 绘制,完成后获取view的高度
                mPoiDetailView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // 添加poi标记
                        PoiOverlay overlay = new MyPoiOverlay(mMap);//传入地图控件管理者引用，初始化自定义标记类
                        mMap.setOnMarkerClickListener(overlay);//为标记添加点击监听，以处理点击事件
                        overlay.setData(result);//传入POI数据，设置overlay标记的相关参数
                        overlay.addToMap();//将所有overlay标记添加到地图上
                        int PaddingBootom = mPoiDetailView.getMeasuredHeight();//获取 view 的高度
                        int padding = 50;
                        overlay.zoomToSpanPaddingBounds(padding, padding, padding, PaddingBootom);// 设置显示在规定宽高中的地图地理范围
                        mPoiDetailView.getViewTreeObserver().removeOnGlobalLayoutListener(this);// 加载完后需要移除View的监听
                        showNearbyArea(ll, radius);//绘制检索范围（圆圈）
                    }
                });
            }

            //POI检索结果文字形式
            mAllPoi = result.getAllPoi();
            if (mAllPoi != null) {
                sb1.append("POI检索结果为:" + "\n");
                for (PoiInfo poiInfo : mAllPoi) {
                    sb1.append("Name:" + poiInfo.getName() + ", ");
                    sb1.append("Address:" + poiInfo.getAddress() + ", ");
                    sb1.append("Uid:" + poiInfo.getUid() + ", ");
                    sb1.append("Location:" + poiInfo.getLocation().latitude + "," + poiInfo.getLocation().longitude + "\n");
                }
            } else {
                sb1.append("POI检索失败！\n");
            }
            updateMap(mPoiResult, sb1.toString());
//            mPoiDetailView.setVisibility(View.VISIBLE);//显示文字结果

        }

        @Override
        public void onGetPoiDetailResult(PoiDetailResult poiDetailResult) {

        }

        @Override
        public void onGetPoiDetailResult(PoiDetailSearchResult poiDetailSearchResult) {

        }

        @Override
        public void onGetPoiIndoorResult(PoiIndoorResult poiIndoorResult) {

        }
    };

    private class MyPoiOverlay extends PoiOverlay {
        MyPoiOverlay(BaiduMap baiduMap) {
            super(baiduMap);
        }

        @Override
        public boolean onPoiClick(int index) {
            super.onPoiClick(index);
            PoiInfo poi = getPoiResult().getAllPoi().get(index);
            Toast.makeText(MainActivity.this, poi.address, Toast.LENGTH_LONG).show();
            return true;
        }
    }

    //开始检索
    public void searchNearby(int rad, int num) {
        KeybordUtil.closeKeybord(this);
        // 配置请求参数
        PoiNearbySearchOption nearbySearchOption = new PoiNearbySearchOption()
                .keyword("停车场") // 检索关键字
                .location(ll) // 经纬度
                .radius(rad) // 检索半径 单位： m
                .pageNum(num) // 分页编号
                .radiusLimit(false)
                .scope(2);
        // 发起检索
        poiSearchService.getPoiOb().searchNearby(nearbySearchOption);
        System.out.println(radius + " + " + num);
    }

    //更新textview
    public void updateMap(final TextView text, final String str) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                text.post(new Runnable() {
                    @Override
                    public void run() {
                        text.setText(str);
                    }
                });
            }
        }).start();
    }

    //对周边检索的范围进行绘制
    public void showNearbyArea(LatLng center, int radius) {
        //添加检索中心点图标
        BitmapDescriptor centerBitmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_center);
        MarkerOptions ooMarker = new MarkerOptions().position(center).icon(centerBitmap);
        mMap.addOverlay(ooMarker);
        OverlayOptions ooCircle = new CircleOptions()
                .fillColor(0x1033b5e5)
                .center(center)
                .stroke(new Stroke(2, 0xFFFF00FF))
                .radius(radius);
        mMap.addOverlay(ooCircle);
    }


    //添加注册对地图事件的消息响应（单击）
    private void initClickListener() {
        mMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            //单击地图
            @Override
            public void onMapClick(LatLng point) {
                ll = point;
                updateMapState();
            }

            //单击地图中的POI点
            @Override
            public void onMapPoiClick(MapPoi poi) {
                ll = poi.getPosition();//读取点击位置信息，将其读取至ll变量中
                updateMapState();
            }
        });
    }

    //更新地图状态显示标记，读取标记点的位置信息
    private void updateMapState() {
        MarkerOptions ooA = new MarkerOptions().position(ll).icon(mbitmap);//初始化标记，并设置具体位置
        mMap.clear();//清空地图标记
        mMap.addOverlay(ooA);//添加标记
    }

    public static class KeybordUtil {
        //关闭软键盘
        public static void closeKeybord(Activity activity) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
            }
        }
    }

    //初始化导航服务
    public void initNavi() {
        BaiduNaviManagerFactory.getBaiduNaviManager().init(MainActivity.this.getApplicationContext(),
                Environment.getExternalStorageDirectory().toString(), "PNBmap", new IBaiduNaviManager.INaviInitListener() {

                    @Override
                    public void onAuthResult(int status, String msg) {
                        String result;
                        if (0 == status) {
                            result = "key校验成功!";
                        } else {
                            result = "key校验失败, " + msg;
                        }
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void initStart() {
                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                "百度导航引擎初始化开始", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void initSuccess() {
                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                "百度导航引擎初始化成功", Toast.LENGTH_SHORT).show();
                        // 初始化tts,选择使用内置TTS
                        BaiduNaviManagerFactory.getTTSManager().initTTS(getApplicationContext(),
                                Environment.getExternalStorageDirectory().toString(), "PNBmap", "11213224");
                    }

                    @Override
                    public void initFailed(int errCode) {
                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                "百度导航引擎初始化失败 " + errCode, Toast.LENGTH_SHORT).show();
                    }
                });

    }

    //开始进行路径规划，并启动导航组件
    public void startPlanAndNavi(LatLng start, LatLng end) {
        Intent intent_navi = new Intent(this, NaviActivity.class);
        startActivity(intent_navi);
        System.out.println("正在启动！！！\n");

        //生成始节点和终节点
        BNRoutePlanNode startNode = new BNRoutePlanNode.Builder()
                .latitude(start.latitude)
                .longitude(start.longitude)
                .coordinateType(BNRoutePlanNode.CoordinateType.BD09LL)
                .build();
        BNRoutePlanNode endNode = new BNRoutePlanNode.Builder()
                .latitude(end.latitude)
                .longitude(end.longitude)
                .coordinateType(BNRoutePlanNode.CoordinateType.BD09LL)
                .build();

        //使用list列表容器盛纳节点
        List<BNRoutePlanNode> list = new ArrayList<>();list.add(startNode);list.add(endNode);

        //根据指定参数进行路线规划，并自动做好进入导航的准备
        BaiduNaviManagerFactory.getRoutePlanManager().routeplanToNavi(
                list,
                IBNRoutePlanManager.RoutePlanPreference.ROUTE_PLAN_PREFERENCE_DEFAULT,
                null,
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_START:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路开始", Toast.LENGTH_SHORT).show();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_SUCCESS:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路成功", Toast.LENGTH_SHORT).show();
                                // 躲避限行消息
                                Bundle infoBundle = (Bundle) msg.obj;
                                if (infoBundle != null) {
                                    String info = infoBundle.getString(
                                            BNaviCommonParams.BNRouteInfoKey.TRAFFIC_LIMIT_INFO
                                    );
                                    Log.d("OnSdkDemo", "info = " + info);
                                }
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_FAILED:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路失败", Toast.LENGTH_SHORT).show();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_TO_NAVI:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路成功准备进入导航", Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(MainActivity.this,
                                        NaviActivity.class);

                                startActivity(intent);
                                break;
                            default:
                                // nothing
                                break;
                        }
                    }
                });


    }

}