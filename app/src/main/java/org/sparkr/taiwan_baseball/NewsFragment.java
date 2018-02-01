package org.sparkr.taiwan_baseball;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.sparkr.taiwan_baseball.Model.News;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link NewsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NewsFragment extends Fragment {

    private OkHttpClient client = new OkHttpClient();
    private List newsList;
    private NewsAdapter adapter;
    private int page = 0;
    private int previousTotal = 0;
    private int visibleThreshold = 4;
    private Boolean loading = true;
    int firstVisibleItem, visibleItemCount, totalItemCount;

    public NewsFragment() {
        // Required empty public constructor

    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment NewsFragment.
     */
    public static NewsFragment newInstance() {
        NewsFragment fragment = new NewsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

        newsList = new ArrayList<>();
        adapter = new NewsAdapter(newsList);
        fetchNews(page);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_news, container, false);

        final RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.newsRecyclerView);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this.getContext());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public Boolean isScrolled = false;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if(newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    isScrolled = true;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if(!isScrolled) { return; }

                visibleItemCount = recyclerView.getChildCount();
                totalItemCount = layoutManager.getItemCount();
                firstVisibleItem = layoutManager.findFirstVisibleItemPosition();

                if(loading) {
                    if(totalItemCount > previousTotal) {
                        loading = false;
                        previousTotal = totalItemCount;
                    }
                }

                if (!loading && ((totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold))) {
                    page++;
                    fetchNews(page);

                    loading = true;
                }

            }
        });

        // Inflate the layout for this fragment
        return view;
    }

    private void fetchNews(final int newPage) {
        Request request = new Request.Builder().url(this.getString(R.string.CPBLSourceURL) + "news/lists/news_lits.html?per_page=" + newPage).build();
        Call mcall = client.newCall(request);
        mcall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {//这是Activity的方法，会在主线程执行任务
                    @Override
                    public void run() {
                        Toast.makeText(getContext(), "發生錯誤，請稍後再試。", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String resStr = response.body().string();
                News news;

                try {
                    Document doc = Jsoup.parse(resStr);

                    String topNewsTitle = doc.select(".news_head_title > a").text();
                    String topNewsDate = doc.select(".news_head_date").text();
                    String topNewsUrl = doc.select(".games_news_pic > a").attr("href").toString();
                    String topNewsImageUrl = doc.select(".games_news_pic > a > img").attr("src").toString();

                    if(!topNewsTitle.isEmpty()) {
                        news = new News(topNewsTitle, topNewsDate, topNewsImageUrl, topNewsUrl);
                        newsList.add(news);
                    }

                    Elements nodes = doc.select(".news_row");
                    for(Element node: nodes) {
                        if (node.select(".news_row_date").text().isEmpty()) {continue;}

                        String newstitle = node.select(".news_row_cont > div > a.news_row_title").text().trim();
                        String tmpeDate = node.select(".news_row_date").text().trim();
                        String newsDate = tmpeDate;
                        String newsImageUrl = node.select(".news_row_pic > img").attr("src").toString();
                        String newsUrl = node.select(".news_row_cont > div > a").attr("href").toString();

                        news = new News(newstitle, newsDate, newsImageUrl, newsUrl);
                        newsList.add(news);
                    }

                    adapter.setOnClick(new NewsAdapter.OnItemClicked(){
                        @Override
                        public void onItemClick(int position) {
                            News selectedNews = (News) newsList.get(position);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getActivity().getString(R.string.CPBLSourceURL) + selectedNews.getNewsUrl()));
                            startActivity(intent);
                        }
                    });

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();

                            if(getActivity().findViewById(R.id.loadingPanel).getVisibility() == View.VISIBLE) {
                                getActivity().findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                            }

                        }
                    });


                } catch (Exception e) {
                    Log.d("error:", e.toString());
                }
            }
        });
    }

    public static class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.ViewHolder> {

        private List<News> news;
        private OnItemClicked onClick;

        public interface OnItemClicked {
            void onItemClick(int position);
        }

        public NewsAdapter(List<News> news) {
            this.news = news;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView newsTitleTextView;
            private final TextView newsDateTextView;
            private final ImageView newsImageView;
            private String newsURL;

            public ViewHolder(View itemView) {
                super(itemView);

                newsTitleTextView = (TextView) itemView.findViewById(R.id.newsTitleTextView);
                newsDateTextView = (TextView) itemView.findViewById(R.id.newsDateTextView);
                newsImageView = (ImageView) itemView.findViewById(R.id.newsImageView);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view = LayoutInflater.from(context).inflate(R.layout.news_list, parent, false);
            ViewHolder viewHolder = new ViewHolder(view);

            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, final int position) {
            News newsData = news.get(position);
            holder.newsTitleTextView.setText(newsData.getTitle());
            holder.newsDateTextView.setText(newsData.getDate());
            holder.newsURL = newsData.getNewsUrl();
            Glide.with(holder.newsImageView.getContext()).load(newsData.getImageUrl()).centerCrop().into(holder.newsImageView);

            holder.newsImageView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    onClick.onItemClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return news.size();
        }

        public void setOnClick(OnItemClicked onClick) {
            this.onClick = onClick;
        }
    }

}
