package com.example.bmap_debug1.service;

import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiBoundSearchOption;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiNearbySearchOption;
import com.baidu.mapapi.search.poi.PoiSearch;

public class PoiSearchService {
    private PoiSearch mPoiSearch = null;

    public PoiSearchService(OnGetPoiSearchResultListener listener) {
        //创建POI检索实例
        mPoiSearch = PoiSearch.newInstance();
        //注册POI检索监听器
        mPoiSearch.setOnGetPoiSearchResultListener(listener);
    }

    //城市内检索
    public boolean poiSearchCity(String city, String keyword, int pageNum) {
        return mPoiSearch.searchInCity(new PoiCitySearchOption()
                .city(city) //必填
                .keyword(keyword) //必填
                .pageNum(pageNum));
    }

    //周边检索
    public boolean poiSearchNearby(LatLng loc,int radius,String keyword,int pageNum){
        // 配置请求参数
        PoiNearbySearchOption nearbySearchOption = new PoiNearbySearchOption()
                .location(loc) // 经纬度
                .radius(radius) // 检索半径 单位： m
                .keyword(keyword) // 检索关键字
                .pageNum(pageNum) // 分页编号
                .radiusLimit(false)// 是否严格限定召回结果在设置检索半径范围内,(默认值为false)设置为true时会影响返回结果中total准确性及每页召回poi数量
                // 检索结果详细程度：取值为1 或空，则返回基本信息；取值为2，返回检索POI详细信息
                .scope(2);
        // 发起检索
        return mPoiSearch.searchNearby(nearbySearchOption);
    }

    //矩形区域检索
    public void poiSearchBounds(LatLng loc1,LatLng loc2,String keyword){
        LatLngBounds searchBounds = new LatLngBounds.Builder()
                .include(loc1)
                .include(loc2)
                .build();
        mPoiSearch.searchInBound(new PoiBoundSearchOption()
                .bound(searchBounds)
                .keyword(keyword));
    }

    public PoiSearch getPoiOb(){
        return this.mPoiSearch;
    }
}
