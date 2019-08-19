package com.example.nabeo.harmaster;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.media.Image;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {
    private final static int STOP = 1;
    private final static int WALKING = 2;
    private final static int UPSTAIRS = 3;
    private final static int DOWNSTAIRS = 4;
    private final static int UPELEV = 5;
    private final static int DOWNELEV = 6;
    private final static int UPESC = 7;
    private final static int DOWNESC = 8;
    private final static int RUNNING = 9;
    private final static int BICYCLE = 10;
    private final static int TRAIN = 11;
    private final static int BUS = 12;

    private LayoutInflater mInflater;
    private Context mContext;
    private OnRecyclerListener mListener;
    private ArrayList<ActivityItem> mData;
    private ActivityManager mActivityManager;
    private boolean mLargeHeap;
    //private Picasso mPicasso;

    public RecyclerAdapter(MainActivity mainActivity, ArrayList<ActivityItem> data){
        mContext = mainActivity;
        mData = data;
        mInflater = LayoutInflater.from(mContext);

        ActivityManager am = (ActivityManager) mainActivity.getApplication().getSystemService(Context.ACTIVITY_SERVICE);
        boolean largeHeap = (mainActivity.getApplication().getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
        int memoryClass = am.getMemoryClass();
        if(largeHeap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            memoryClass = am.getLargeMemoryClass();
        }
        int cacheSize = 1024*1024*(memoryClass/8);
        Picasso picasso = new Picasso.Builder(mainActivity.getApplication()).memoryCache(new LruCache(cacheSize)).build();
        Picasso.setSingletonInstance(picasso);
    }

    @Override
    public RecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i){
//        switch (i){
//            case 1:
//                return new ActivityViewHolder(mInflater.inflate(R.layout.pictogram, viewGroup, false));
//
//        }
        return  new ActivityViewHolder(mInflater.inflate(R.layout.pictogram, viewGroup, false));
    }

//    @Override
//    public void onBindViewHolder(RecyclerAdapter.ViewHolder viewHolder, int i, List<ActivityItem> data){
//
//    }

    @Override
    public int getItemViewType(int position){
//        if(mData.get(position).equals("Stop")){
//            return STOP;
//        }else if(mData.get(position).equals("Walking")){
//            return WALKING;
//        }else if(mData.get(position).equals("Upstairs")){
//            return UPSTAIRS;
//        }else if(mData.get(position).equals("Downstairs")){
//            return DOWNSTAIRS;
//        }else if(mData.get(position).equals("Up-Elevator")){
//            return UPELEV;
//        }else if(mData.get(position).equals("Down-Elevator")){
//            return DOWNELEV;
//        }else if(mData.get(position).equals("Up-Escalator")){
//            return UPESC;
//        }else if(mData.get(position).equals("Down-Escalator")){
//            return DOWNESC;
//        }else if(mData.get(position).equals("Running")){
//            return RUNNING;
//        }else if(mData.get(position).equals("Bicycle")){
//            return BICYCLE;
//        }else if(mData.get(position).equals("Train")){
//            return TRAIN;
//        }else if(mData.get(position).equals("Bus")){
//            return BUS;
//        }else{
//            return 0;
//        }
        return 1;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int i){
//        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mListener.onRecyclerClicked(v, i);
//            }
//        });
        Log.d("hoge", String.valueOf(i));
        ((ActivityViewHolder)viewHolder).onBindItemViewHolder(mData.get(i));
    }

    @Override
    public int getItemCount(){
        if(mData != null){
            return mData.size();
        }else{
            return 0;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder{
        public ViewHolder(View itemView){
            super(itemView);
        }
    }

    class ActivityViewHolder extends RecyclerAdapter.ViewHolder{
        ImageView imageView;
        TextView timetv;
        TextView distancetv;

        public ActivityViewHolder(View itemView){
            super(itemView);
            imageView = (ImageView)itemView.findViewById(R.id.pict_image);
            timetv = (TextView)itemView.findViewById(R.id.time_view);
            distancetv = (TextView)itemView.findViewById(R.id.distance_view);
            //Picasso.get().load("android.resource://com.example.nabeo.harmaster/drawable/arrow.png").into(((ImageView) itemView.findViewById(R.id.imageView)));
            ((ImageView)itemView.findViewById(R.id.imageView)).setImageResource(R.drawable.arrow);
        }

        public void onBindItemViewHolder(ActivityItem item){
            String state = item.getState();
            if(state.equals("Stop")) imageView.setImageResource(R.drawable.stop);
            else if(state.equals("Walking")) imageView.setImageResource(R.drawable.walking);
            else if(state.equals("Upstairs")) imageView.setImageResource(R.drawable.up_stair);
            else if(state.equals("Downstairs")) imageView.setImageResource(R.drawable.down_stair);
            else if(state.equals("Up-Elevator")) imageView.setImageResource(R.drawable.up_ele);
            else if(state.equals("Down-Elevator")) imageView.setImageResource(R.drawable.down_ele);
            else if(state.equals("Running")) imageView.setImageResource(R.drawable.running);
            else if(state.equals("Up-Escalator")) imageView.setImageResource(R.drawable.up_esc);
            else if(state.equals("Down-Escalator")) imageView.setImageResource(R.drawable.down_esc);
            else if(state.equals("Bicycle")) imageView.setImageResource(R.drawable.bicycle);
            else if(state.equals("Train")) imageView.setImageResource(R.drawable.train_icon);
            else if(state.equals("Bus")) imageView.setImageResource(R.drawable.bus);

            Log.d("onBindItemViewHolder", String.valueOf(item.getTime()));
            timetv.setText("Time: " + item.getTime());
            distancetv.setText("Distance: " + item.getDistance());
        }
    }
}
