
package com.brian.codeblog.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.brian.common.tools.Env;
import com.brian.codeblog.datacenter.preference.SearchPreference;
import com.brian.codeblog.stat.UsageStatsManager;
import com.brian.codeblog.model.Bloger;
import com.brian.codeblog.model.SearchResult;
import com.brian.codeblog.parser.CSDNHtmlParser;
import com.brian.codeblog.proctocol.HttpGetSearchBlogRequest;
import com.brian.common.datacenter.network.IResponseCallback;
import com.brian.common.tools.CommonAdapter;
import com.brian.common.tools.GsonHelper;
import com.brian.common.utils.LogUtil;
import com.brian.common.utils.ResourceUtil;
import com.brian.common.utils.SDKUtil;
import com.brian.common.utils.ToastUtil;
import com.brian.common.utils.UIUtil;
import com.brian.common.view.RefreshLayout;
import com.brian.common.view.TitleBar;
import com.brian.csdnblog.R;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SearchActivity extends BaseActivity {
    private static final String TAG = SearchActivity.class.getSimpleName();

    private static final int MAX_HISTORY_COUNT = 5;

    @BindView(R.id.title_bar) TitleBar mTitleBar;
    @BindView(R.id.et_search) EditText mSearchInput;
    @BindView(R.id.bt_search) Button mSearchBtn;
    @BindView(R.id.lv_result) ListView mResultListView;
    @BindView(R.id.swipe_container) RefreshLayout mRefreshLayout;
    @BindView(R.id.progressbar) ProgressBar mProgressBar;
    @BindView(R.id.search_history_ll) View mHistoryLy;
    @BindView(R.id.search_history_lv) ListView mHistoryList;
    @BindView(R.id.clear_history_btn) Button mClearHistoryBtn;
    private View mFooterLayout;

    private CommonAdapter<SearchResult> mSearchResultAdapter = null;
    private CommonAdapter<String> mHistoryAdapter = null;

    private int mCurrentPage = 1;
    private String mInputText = "";

    public static void startActivity(Activity activity) {
        Intent intent = new Intent();
        intent.setClass(activity, SearchActivity.class);
        activity.startActivity(intent);
    }
    
    
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ButterKnife.bind(this);

        initUI();
        initListener();
    }

    private void initUI() {
        mFooterLayout = getLayoutInflater().inflate(R.layout.loading_footer, null);
        mFooterLayout.setVisibility(View.GONE);
        mResultListView.addFooterView(mFooterLayout);
        mRefreshLayout.setChildView(mResultListView);

        SDKUtil.showSoftInputOnfocus(mSearchInput, true);

        mTitleBar.setTitle("CSDN搜索");
        mTitleBar.setRightImageVisible(View.INVISIBLE);

        mHistoryAdapter = new CommonAdapter<String>(Env.getContext(), getSearchHistory(), R.layout.item_search_history) {
            @Override
            public void convert(ViewHolder holder, final String item) {
                holder.setText(R.id.contentTextView, item);
                holder.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSearchInput.setText(item);
                        doSearch();
                    }
                });
            }
        };
        mHistoryList.setAdapter(mHistoryAdapter);

        mSearchResultAdapter = new CommonAdapter<SearchResult>(Env.getContext(), null, R.layout.item_list_search) {
            private ForegroundColorSpan mColorSpanName = new ForegroundColorSpan(ResourceUtil.getColor(R.color.light_blue));
            @Override
            public void convert(ViewHolder holder, final SearchResult item) {
                holder.setText(R.id.title, item.title);
                holder.setText(R.id.authorTime, item.authorTime);
                holder.setText(R.id.searchDetail, item.searchDetail);

                TextView nameView = holder.getView(R.id.authorTime);
                Bloger bloger = Bloger.fromJson(item.blogerJson);
                if (bloger != null && !TextUtils.isEmpty(bloger.nickName) && !TextUtils.isEmpty(item.authorTime)) {
                    SpannableStringBuilder builder = new SpannableStringBuilder(item.authorTime);
                    int indexStart = item.authorTime.indexOf(bloger.nickName);
                    builder.setSpan(mColorSpanName, indexStart, indexStart + bloger.nickName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameView.setText(builder);
                } else {
                    nameView.setText(item.authorTime);
                }

                nameView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!TextUtils.isEmpty(item.blogerID)) {
                            LogUtil.log(item.blogerJson);
                            Bloger bloger = Bloger.fromJson(item.blogerJson);
                            if (bloger != null) {
                                BlogerBlogListActivity.startActivity(SearchActivity.this, item.type, bloger);
                                UsageStatsManager.reportData(UsageStatsManager.USAGE_BLOGER_ENTR, "bloglist");
                            }
                        }
                    }
                });

                holder.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BlogContentActivity.startActvity(SearchActivity.this, item);
                    }
                });
            }

            private int lastPosition = -1;
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Animation animation = AnimationUtils.loadAnimation(mContext, (position > lastPosition) ? R.anim.anim_up_from_bottom : R.anim.anim_down_from_top);
                view.startAnimation(animation);
                lastPosition = position;
                return view;
            }
        };
        mResultListView.setAdapter(mSearchResultAdapter);

        mResultListView.setVisibility(View.INVISIBLE);
    }

    private void initListener() {
        mTitleBar.setLeftListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mSearchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || (event!=null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });

        mSearchInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                mHistoryLy.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
            }
        });

        mSearchBtn.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                doSearch();
            }
        });

        mRefreshLayout.setOnLoadListener(new RefreshLayout.OnLoadListener() {
            @Override
            public void onLoad() {
                if (!TextUtils.isEmpty(mInputText)) {
                    loadListData(getSearchUrl(mInputText, mCurrentPage));
                }
            }
        });

        mClearHistoryBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearHistoryList();
                mClearHistoryBtn.setVisibility(View.GONE);
            }
        });
    }

    private void doSearch() {
        UIUtil.hideKeyboard(mSearchInput);
        mInputText = mSearchInput.getText().toString()
                .replaceAll(
                        "[`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？]",
                        "");
        if (!TextUtils.isEmpty(mInputText)) {
            mInputText = mInputText.trim().replace(' ', '+');
            String url = getSearchUrl(mInputText, 1);
            mCurrentPage = 1;
            loadListData(url);

            addSearchHistory(mInputText);
            UsageStatsManager.reportData(UsageStatsManager.USAGE_SEARCH, mInputText);
        } else {
            ToastUtil.showMsg("请输入适当关键字");
        }
    }


    private String getSearchUrl(String keyWord, int page) {
        CSDNHtmlParser parser = CSDNHtmlParser.getInstance();
        return parser.getSearchUrlByKeyword(keyWord, page);
    }

    private void loadListData(String loadUrl) {
        if (mSearchResultAdapter.isEmpty()) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mFooterLayout.setVisibility(View.VISIBLE);
        }

        HttpGetSearchBlogRequest.RequestParam param = new HttpGetSearchBlogRequest.RequestParam();
        param.url = loadUrl;
        new HttpGetSearchBlogRequest().request(param, new IResponseCallback<HttpGetSearchBlogRequest.ResultData>() {
            @Override
            public void onSuccess(HttpGetSearchBlogRequest.ResultData resultData) {
                mRefreshLayout.setLoading(false);
                mHistoryLy.setVisibility(View.GONE);

                if (resultData.blogInfoList == null || resultData.blogInfoList.isEmpty()) {
                    if (mSearchResultAdapter.isEmpty()) {
                        // 没有搜索到结果的提示
                        UsageStatsManager.reportData(UsageStatsManager.EXP_EMPTY_SEARCH, mInputText);
                    }
                } else {
                    if (mCurrentPage <= 1) {
                        mSearchResultAdapter.removeAllDatas();
                    }
                    mResultListView.setVisibility(View.VISIBLE);
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mCurrentPage++;
                    mSearchResultAdapter.addDatas(resultData.blogInfoList);
                }
            }

            @Override
            public void onError(int rtn, String msg) {
                LogUtil.e("msg=" + msg);
            }

            @Override
            public void onFailure(int errorCode, String msg) {
                LogUtil.e("errorCode=" + errorCode);
            }
        });
    }

    private List<String> getSearchHistory() {
        List historyList = new GsonHelper<String>().fromJson(SearchPreference.getInstance().getHistoryListJson());
        if (historyList == null || historyList.size() <= 0) {
            mClearHistoryBtn.setVisibility(View.GONE);
        } else {
            mClearHistoryBtn.setVisibility(View.VISIBLE);
        }
        return historyList;
    }

    private void addSearchHistory(String keyWord) {
        if (mHistoryAdapter.containData(keyWord)) {
            mHistoryAdapter.removeData(keyWord);
        }
        while (mHistoryAdapter.getCount() >= MAX_HISTORY_COUNT) {
            mHistoryAdapter.removeDataAt(mHistoryAdapter.getCount()-1);
        }
        mHistoryAdapter.addData(0, keyWord);
        SearchPreference.getInstance().setHistoryListJson(new GsonHelper<String>().convert2String(mHistoryAdapter.getDatas()));
        if (!mHistoryAdapter.isEmpty()) {
            mClearHistoryBtn.setVisibility(View.VISIBLE);
        }
    }

    private void clearHistoryList() {
        mHistoryAdapter.removeAllDatas();
        SearchPreference.getInstance().setHistoryListJson(new GsonHelper<String>().convert2String(mHistoryAdapter.getDatas()));
    }
}
