package com.wucang.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wucang.inventory.data.CsvExporter;
import com.wucang.inventory.data.InventoryItem;
import com.wucang.inventory.data.InventoryQuery;
import com.wucang.inventory.data.InventoryRepository;
import com.wucang.inventory.io.ExportFiles;
import com.wucang.inventory.io.PhotoStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.LruCache;

public class MainActivity extends Activity {
    private InventoryRepository repository;
    private PhotoStore photos;
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
    private List<InventoryItem> items=new ArrayList<>(), visible=new ArrayList<>();
    private final Set<String> selectedTags=new HashSet<>();
    private String search="",category="",status="all",sort="recent",pendingExport;
    private int tab=0;
    private boolean manageTags=false,exportBusy=false;
    private LinearLayout root,page,categoryRow,statusRow;
    private TextView countText,totalKinds,totalQuantity,totalLow,filterButton;
    private InventoryAdapter adapter;
    private ListView inventoryList;
    private LinearLayout emptyView;
    private static final int SAVE_CSV=41;

    @Override public void onCreate(Bundle state){super.onCreate(state);repository=new InventoryRepository(this);photos=new PhotoStore(this);
        if(state!=null){search=state.getString("search","");category=state.getString("category","");status=state.getString("status","all");sort=state.getString("sort","recent");tab=state.getInt("tab");manageTags=state.getBoolean("manageTags");pendingExport=state.getString("pendingExport");ArrayList<String> tags=state.getStringArrayList("tags");if(tags!=null)selectedTags.addAll(tags);}
        load();render();}
    @Override protected void onResume(){super.onResume();if(repository!=null){load();render();}}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("search",search);out.putString("category",category);out.putString("status",status);out.putString("sort",sort);out.putInt("tab",tab);out.putBoolean("manageTags",manageTags);out.putString("pendingExport",pendingExport);out.putStringArrayList("tags",new ArrayList<>(selectedTags));}
    private void load(){items=repository.getItems();if(!category.isEmpty()&&!category.equals("__uncategorized__")&&!repository.getCategories().contains(category))category="";selectedTags.retainAll(repository.getTags());}
    private void render(){
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(this,root);setContentView(root);
        LinearLayout header=Ui.row(this);Ui.pad(header,20,14,16,12);
        View logo=Ui.icon(this,"box",Ui.ACCENT);header.addView(logo,new LinearLayout.LayoutParams(d(30),d(30)));
        LinearLayout brand=Ui.column(this);Ui.pad(brand,10,0,0,0);brand.addView(Ui.bold(this,"物仓",24,Ui.INK));Ui.gap(brand,5);brand.addView(Ui.text(this,"每件物品，井井有条",11,Ui.MUTED));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout export=Ui.row(this);Ui.pad(export,12,10,8,10);export.addView(Ui.icon(this,"export",Ui.INK),new LinearLayout.LayoutParams(d(20),d(20)));TextView label=Ui.bold(this," 导出",13,Ui.INK);export.addView(label);export.setContentDescription("导出库存清单");export.setBackground(Ui.bg(Ui.WHITE,12,this));export.setOnClickListener(v->chooseExport());header.addView(export);root.addView(header);
        page=Ui.column(this);root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0)inventoryPage();else if(tab==1)managementPage();else overviewPage();
        LinearLayout nav=Ui.row(this);nav.setBackgroundColor(Ui.WHITE);String[] titles={"库存","分类与标签","总览"},icons={"box","tag","chart"};
        for(int i=0;i<3;i++){final int target=i;LinearLayout btn=Ui.column(this);btn.setGravity(Gravity.CENTER);Ui.pad(btn,0,10,0,10);btn.addView(Ui.icon(this,icons[i],i==tab?Ui.ACCENT:Ui.MUTED),new LinearLayout.LayoutParams(d(22),d(22)));Ui.gap(btn,5);TextView navLabel=Ui.bold(this,titles[i],11,i==tab?Ui.ACCENT:Ui.MUTED);navLabel.setGravity(Gravity.CENTER);btn.addView(navLabel);btn.setContentDescription(titles[i]);btn.setOnClickListener(v->{tab=target;render();});nav.addView(btn,new LinearLayout.LayoutParams(0,d(66),1));}
        root.addView(nav);
    }
    private void inventoryPage(){
        LinearLayout top=Ui.column(this);Ui.pad(top,20,4,20,0);page.addView(top);
        LinearLayout summary=Ui.row(this);Ui.pad(summary,18,18,18,18);summary.setBackground(Ui.bg(Ui.INK,18,this));
        totalKinds=stat(summary,"物品种类");totalQuantity=stat(summary,"库存总量");totalLow=stat(summary,"待补充");top.addView(summary);updateStats();Ui.gap(top,16);
        LinearLayout searchBox=Ui.row(this);searchBox.setBackground(Ui.outline(this,Ui.WHITE));Ui.pad(searchBox,13,0,8,0);searchBox.addView(Ui.icon(this,"search",Ui.MUTED),new LinearLayout.LayoutParams(d(20),d(20)));
        EditText query=Ui.input(this,"搜索名称、标签、位置…");query.setId(R.id.inventory_search);query.setTextSize(14);query.setBackgroundColor(android.graphics.Color.TRANSPARENT);query.setText(search);query.setContentDescription("搜索库存");searchBox.addView(query,new LinearLayout.LayoutParams(0,d(49),1));TextView clear=Ui.text(this,"×",24,Ui.MUTED);clear.setGravity(Gravity.CENTER);clear.setContentDescription("清空搜索");clear.setOnClickListener(v->query.setText(""));searchBox.addView(clear,new LinearLayout.LayoutParams(d(40),d(44)));top.addView(searchBox);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){search=s.toString();refreshList();}public void afterTextChanged(Editable e){}});
        Ui.gap(top,10);categoryRow=horizontal(top);renderCategories();statusRow=horizontal(top);renderStatuses();
        LinearLayout line=Ui.row(this);Ui.pad(line,0,10,0,10);countText=Ui.bold(this,"",14,Ui.INK);line.addView(countText,new LinearLayout.LayoutParams(0,-2,1));TextView sortBtn=Ui.text(this,"排序 ⇅",12,Ui.MUTED);Ui.pad(sortBtn,12,8,0,8);sortBtn.setOnClickListener(v->chooseSort());line.addView(sortBtn);top.addView(line);
        FrameLayout content=new FrameLayout(this);page.addView(content,new LinearLayout.LayoutParams(-1,0,1));inventoryList=new ListView(this);inventoryList.setDivider(null);inventoryList.setClipToPadding(false);inventoryList.setPadding(d(20),0,d(20),d(84));inventoryList.setSelector(android.R.color.transparent);adapter=new InventoryAdapter();inventoryList.setAdapter(adapter);content.addView(inventoryList,new FrameLayout.LayoutParams(-1,-1));
        emptyView=Ui.column(this);emptyView.setGravity(Gravity.CENTER);Ui.pad(emptyView,24,18,24,88);content.addView(emptyView,new FrameLayout.LayoutParams(-1,-1));
        TextView add=Ui.button(this,"＋  新建物品",true);add.setElevation(d(4));FrameLayout.LayoutParams fab=new FrameLayout.LayoutParams(-2,d(52),Gravity.BOTTOM|Gravity.END);fab.setMargins(d(20),d(12),d(20),d(18));content.addView(add,fab);add.setOnClickListener(v->edit(0));refreshList();
    }
    private TextView stat(LinearLayout parent,String label){LinearLayout cell=Ui.column(this);TextView n=Ui.bold(this,"0",27,Ui.WHITE);n.setSingleLine(true);n.setAutoSizeTextTypeUniformWithConfiguration(12,27,1,android.util.TypedValue.COMPLEX_UNIT_SP);cell.addView(n,new LinearLayout.LayoutParams(-1,d(34)));Ui.gap(cell,7);cell.addView(Ui.text(this,label,11,0xFFBAC1CC));parent.addView(cell,new LinearLayout.LayoutParams(0,-2,1));return n;}
    private void updateStats(){if(totalKinds==null)return;long quantity=0;int low=0;for(InventoryItem i:items){quantity+=i.quantity;if(!i.status().equals("normal"))low++;}totalKinds.setText(String.valueOf(items.size()));totalQuantity.setText(String.valueOf(quantity));totalLow.setText(String.valueOf(low));}
    private LinearLayout horizontal(LinearLayout parent){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=Ui.row(this);scroll.addView(row);parent.addView(scroll,new LinearLayout.LayoutParams(-1,d(46)));return row;}
    private void renderCategories(){categoryRow.removeAllViews();addChip(categoryRow,"全部分类",category.isEmpty(),()->{category="";renderCategories();refreshList();});for(String c:repository.getCategories())addChip(categoryRow,c,category.equals(c),()->{category=c;renderCategories();refreshList();});addChip(categoryRow,"未分类",category.equals("__uncategorized__"),()->{category="__uncategorized__";renderCategories();refreshList();});}
    private void renderStatuses(){statusRow.removeAllViews();String[] names={"全部状态","充足","库存不足","已用尽"},keys={"all","normal","low","out"};for(int i=0;i<keys.length;i++){final String s=keys[i];addChip(statusRow,names[i],status.equals(s),()->{status=s;renderStatuses();refreshList();});}filterButton=Ui.chip(this,"标签"+(selectedTags.isEmpty()?" ＋":" · "+selectedTags.size()),!selectedTags.isEmpty());filterButton.setOnClickListener(v->filterTags());statusRow.addView(filterButton);}
    private void addChip(LinearLayout row,String title,boolean selected,Runnable action){TextView c=Ui.chip(this,title,selected);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,d(38));lp.setMargins(0,0,d(7),0);row.addView(c,lp);c.setOnClickListener(v->action.run());}
    private void refreshList(){if(adapter==null||tab!=0)return;visible=InventoryQuery.filter(items,search,category,selectedTags,status,sort);countText.setText("我的库存  ·  "+visible.size());adapter.notifyDataSetChanged();boolean empty=visible.isEmpty();emptyView.setVisibility(empty?View.VISIBLE:View.GONE);inventoryList.setVisibility(empty?View.GONE:View.VISIBLE);if(empty){emptyView.removeAllViews();emptyView.addView(Ui.icon(this,"box",Ui.ACCENT),new LinearLayout.LayoutParams(d(48),d(48)));Ui.gap(emptyView,16);TextView emptyTitle=Ui.bold(this,items.isEmpty()?"从第一件物品开始":"没有匹配的物品",18,Ui.INK);emptyTitle.setGravity(Gravity.CENTER);emptyView.addView(emptyTitle);Ui.gap(emptyView,8);TextView hint=Ui.text(this,items.isEmpty()?"拍张照片，为它取名，库存就有了记录。":"试试其他关键词，或清除筛选条件。",12,Ui.MUTED);hint.setGravity(Gravity.CENTER);emptyView.addView(hint);if(!items.isEmpty()){Ui.gap(emptyView,12);TextView reset=Ui.button(this,"清除筛选",false);reset.setOnClickListener(v->{search="";category="";status="all";selectedTags.clear();render();});emptyView.addView(reset);}}}
    private void chooseSort(){String[] values={"recent","name","quantityAsc","quantityDesc"};String[] labels={"最近更新","名称 A → Z","数量从少到多","数量从多到少"};int checked=0;for(int i=0;i<values.length;i++)if(sort.equals(values[i]))checked=i;new AlertDialog.Builder(this).setTitle("物品排序").setSingleChoiceItems(labels,checked,(dialog,which)->{sort=values[which];refreshList();dialog.dismiss();}).setNegativeButton("取消",null).show();}
    private void filterTags(){List<String> tags=repository.getTags();if(tags.isEmpty()){new AlertDialog.Builder(this).setTitle("还没有标签").setMessage("先到「分类与标签」新建标签，或在编辑物品时添加。").setPositiveButton("去管理",(d,w)->{tab=1;manageTags=true;render();}).setNegativeButton("取消",null).show();return;}boolean[] selected=new boolean[tags.size()];Set<String> draft=new HashSet<>(selectedTags);for(int i=0;i<tags.size();i++)selected[i]=draft.contains(tags.get(i));new AlertDialog.Builder(this).setTitle("筛选标签（同时包含）").setMultiChoiceItems(tags.toArray(new String[0]),selected,(d,w,on)->{if(on)draft.add(tags.get(w));else draft.remove(tags.get(w));}).setPositiveButton("应用",(d,w)->{selectedTags.clear();selectedTags.addAll(draft);renderStatuses();refreshList();}).setNeutralButton("清除",(d,w)->{selectedTags.clear();renderStatuses();refreshList();}).setNegativeButton("取消",null).show();}
    private void edit(long id){Intent intent=new Intent(this,ItemEditorActivity.class);if(id>0)intent.putExtra("item_id",id);startActivity(intent);}
    private void adjust(InventoryItem item,int delta){try{InventoryItem current=repository.getItem(item.id);if(current==null){load();refreshList();return;}long next=(long)current.quantity+delta;if(next<0||next>999999999)return;repository.updateQuantity(item.id,(int)next);load();updateStats();refreshList();}catch(Exception e){error(e);}}
    private class InventoryAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}public InventoryItem getItem(int position){return visible.get(position);}public long getItemId(int position){return getItem(position).id;}
        public View getView(int position,View convert,ViewGroup parent){InventoryItem item=getItem(position);LinearLayout outer=Ui.column(MainActivity.this);Ui.pad(outer,0,0,0,12);LinearLayout card=Ui.column(MainActivity.this);Ui.pad(card,14,14,14,12);card.setBackground(Ui.bg(Ui.WHITE,16,MainActivity.this));outer.addView(card);
            LinearLayout line=Ui.row(MainActivity.this);FrameLayout image=new FrameLayout(MainActivity.this);image.setBackground(Ui.bg(0xFFF1F3F6,12,MainActivity.this));image.setClipToOutline(true);image.addView(Ui.icon(MainActivity.this,"box",0xFFADB4BF),new FrameLayout.LayoutParams(d(34),d(34),Gravity.CENTER));line.addView(image,new LinearLayout.LayoutParams(d(76),d(76)));
            if(item.photoPath!=null&&!item.photoPath.isEmpty()){ImageView pic=new ImageView(MainActivity.this);pic.setScaleType(ImageView.ScaleType.CENTER_CROP);pic.setContentDescription(item.name+"的照片");image.addView(pic,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(pic,item.photoPath);}
            LinearLayout info=Ui.column(MainActivity.this);Ui.pad(info,14,0,0,0);TextView name=Ui.bold(MainActivity.this,item.name,16,Ui.INK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(name);Ui.gap(info,7);TextView cat=Ui.text(MainActivity.this,(item.category.isEmpty()?"未分类":item.category)+(item.location.isEmpty()?"":"  ·  "+item.location),11,Ui.MUTED);cat.setSingleLine(true);cat.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(cat);Ui.gap(info,8);String tags=item.tags.isEmpty()?"暂无标签":"# "+android.text.TextUtils.join("  # ",item.tags);TextView tag=Ui.text(MainActivity.this,tags,11,item.tags.isEmpty()?Ui.MUTED:Ui.ACCENT);tag.setSingleLine(true);tag.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(tag);line.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(line);line.setOnClickListener(v->edit(item.id));line.setContentDescription("编辑物品 "+item.name);
            Ui.gap(card,12);LinearLayout bottom=Ui.row(MainActivity.this);String state=item.status();int color=state.equals("normal")?Ui.GREEN:state.equals("out")?Ui.RED:Ui.ACCENT;TextView badge=Ui.bold(MainActivity.this,"●  "+item.statusLabel(),11,color);bottom.addView(badge,new LinearLayout.LayoutParams(0,-2,1));TextView minus=Ui.button(MainActivity.this,"−",false);Ui.pad(minus,0,0,0,0);minus.setTextSize(22);minus.setMinHeight(d(44));minus.setBackground(Ui.bg(Ui.BG,10,MainActivity.this));minus.setContentDescription("减少 "+item.name+" 数量");minus.setEnabled(item.quantity>0);minus.setAlpha(item.quantity>0?1f:.3f);minus.setOnClickListener(v->adjust(item,-1));bottom.addView(minus,new LinearLayout.LayoutParams(d(44),d(44)));
            TextView qty=Ui.bold(MainActivity.this,item.quantity+" "+item.unit,14,Ui.INK);qty.setGravity(Gravity.CENTER);Ui.pad(qty,10,0,10,0);qty.setContentDescription(item.name+" 库存数量 "+item.quantity);qty.setOnClickListener(v->setQuantity(item));bottom.addView(qty,new LinearLayout.LayoutParams(-2,d(44)));TextView plus=Ui.button(MainActivity.this,"＋",false);Ui.pad(plus,0,0,0,0);plus.setTextColor(Ui.ACCENT);plus.setTextSize(19);plus.setMinHeight(d(44));plus.setBackground(Ui.bg(Ui.PALE,10,MainActivity.this));plus.setContentDescription("增加 "+item.name+" 数量");plus.setOnClickListener(v->adjust(item,1));bottom.addView(plus,new LinearLayout.LayoutParams(d(44),d(44)));card.addView(bottom);return outer;}
    }
    private void loadThumbnail(ImageView view,String path){Bitmap cached=thumbnails.get(path);if(cached!=null){view.setImageBitmap(cached);return;}view.setTag(path);files.execute(()->{Bitmap bitmap=photos.loadThumbnail(path,d(100));if(bitmap!=null){thumbnails.put(path,bitmap);runOnUiThread(()->{if(!isDestroyed()&&path.equals(view.getTag()))view.setImageBitmap(bitmap);});}});}
    private void setQuantity(InventoryItem item){EditText input=Ui.input(this,"库存数量");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(item.quantity));input.selectAll();LinearLayout wrap=Ui.column(this);Ui.pad(wrap,24,8,24,0);wrap.addView(input);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("调整「"+item.name+"」数量").setView(wrap).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{long n=Long.parseLong(input.getText().toString().trim());if(n<0||n>999999999)throw new IllegalArgumentException("数量应在 0～999999999 之间");repository.updateQuantity(item.id,(int)n);load();updateStats();refreshList();dialog.dismiss();}catch(Exception e){input.setError("请输入 0～999999999 的整数");}}));dialog.show();}
    private void managementPage(){
        LinearLayout container=Ui.column(this);Ui.pad(container,20,4,20,20);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(container);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        container.addView(Ui.bold(this,"让每一件物品都有归属",21,Ui.INK));Ui.gap(container,8);container.addView(Ui.text(this,"分类归档，标签关联。查找从此更轻松。",12,Ui.MUTED));Ui.gap(container,20);
        LinearLayout switcher=Ui.row(this);addChip(switcher,"分类",!manageTags,()->{manageTags=false;render();});addChip(switcher,"标签",manageTags,()->{manageTags=true;render();});container.addView(switcher);Ui.gap(container,20);
        List<String> names=manageTags?repository.getTags():repository.getCategories();LinearLayout heading=Ui.row(this);heading.addView(Ui.bold(this,(manageTags?"全部标签":"全部分类")+" · "+names.size(),15,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));TextView add=Ui.button(this,"＋ 新建",false);add.setTextColor(Ui.ACCENT);add.setOnClickListener(v->nameDialog(null));heading.addView(add);container.addView(heading);Ui.gap(container,12);
        if(names.isEmpty()){TextView hint=Ui.text(this,"还没有"+(manageTags?"标签":"分类")+"，点击右上方新建。",14,Ui.MUTED);Ui.pad(hint,0,20,0,20);container.addView(hint);}
        for(String name:names){int count=0;for(InventoryItem item:items)if(manageTags?item.tags.contains(name):item.category.equals(name))count++;LinearLayout row=Ui.row(this);Ui.pad(row,16,15,14,15);row.setBackground(Ui.bg(Ui.WHITE,14,this));View icon=Ui.icon(this,manageTags?"tag":"grid",Ui.ACCENT);row.addView(icon,new LinearLayout.LayoutParams(d(24),d(24)));TextView title=Ui.bold(this,name,15,Ui.INK);Ui.pad(title,14,0,8,0);title.setMaxLines(2);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,count+" 件物品  ⋯",12,Ui.MUTED));row.setOnClickListener(v->manageName(name));container.addView(row);Ui.gap(container,10);}
        Ui.gap(container,12);TextView foot=Ui.text(this,"重命名会同步到已有物品。删除分类或标签时，物品记录仍然保留。",12,Ui.MUTED);foot.setLineSpacing(d(4),1);container.addView(foot);
    }
    private void manageName(String name){boolean tags=manageTags;new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"查看物品","重命名","删除"+(tags?"标签":"分类")},(dialog,which)->{if(which==0){search="";category=tags?"":name;status="all";selectedTags.clear();if(tags)selectedTags.add(name);tab=0;render();}else if(which==1)nameDialog(name);else new AlertDialog.Builder(this).setTitle("删除「"+name+"」？").setMessage(tags?"此标签会从相关物品中移除，物品记录保留。":"此分类中的物品将改为未分类，物品记录保留。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{if(tags)repository.deleteTag(name);else repository.deleteCategory(name);load();render();}catch(Exception e){error(e);}}).show();}).show();}
    private void nameDialog(String oldName){boolean tags=manageTags;EditText input=Ui.input(this,tags?"例如：常用、备用":"例如：摄影器材");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});if(oldName!=null)input.setText(oldName);LinearLayout wrap=Ui.column(this);Ui.pad(wrap,24,8,24,0);wrap.addView(input);AlertDialog dialog=new AlertDialog.Builder(this).setTitle((oldName==null?"新建":"重命名")+(tags?"标签":"分类")).setView(wrap).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{String name=input.getText().toString().trim();if(oldName==null){if(tags)repository.addTag(name);else repository.addCategory(name);}else{if(tags)repository.renameTag(oldName,name);else repository.renameCategory(oldName,name);}load();render();dialog.dismiss();}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();}
    private void overviewPage(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.column(this);Ui.pad(body,20,8,20,24);scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));body.addView(Ui.bold(this,"库存，一目了然",23,Ui.INK));Ui.gap(body,8);body.addView(Ui.text(this,"每次数量变更，都会即时保存在本机。",12,Ui.MUTED));Ui.gap(body,22);
        int low=0,out=0;long count=0;for(InventoryItem i:items){count+=i.quantity;if(i.status().equals("low"))low++;if(i.status().equals("out"))out++;}
        LinearLayout summary=Ui.row(this);summary.setBackground(Ui.bg(Ui.INK,18,this));Ui.pad(summary,18,20,18,20);stat(summary,"物品种类").setText(String.valueOf(items.size()));stat(summary,"库存总量").setText(String.valueOf(count));stat(summary,"分类数量").setText(String.valueOf(repository.getCategories().size()));body.addView(summary);Ui.gap(body,18);
        final String alertStatus=out>0?"out":"low";TextView warning=Ui.button(this,"待补充  "+low+" 种库存不足 · "+out+" 种已用尽",false);warning.setTextColor(Ui.ACCENT);warning.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);warning.setOnClickListener(v->{tab=0;status=alertStatus;category="";selectedTags.clear();search="";render();});body.addView(warning);Ui.gap(body,24);body.addView(Ui.section(this,"分类分布"));
        List<String> categories=new ArrayList<>(repository.getCategories());categories.add("");for(String c:categories){int n=0;long q=0;for(InventoryItem i:items)if(i.category.equals(c)){n++;q+=i.quantity;}if(n==0)continue;LinearLayout row=Ui.row(this);Ui.pad(row,0,10,0,10);row.addView(Ui.text(this,c.isEmpty()?"未分类":c,14,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,n+" 种 · "+q+" 总量",12,Ui.MUTED));body.addView(row);}
        if(items.isEmpty())body.addView(Ui.text(this,"添加物品后，这里会显示库存分布。",13,Ui.MUTED));Ui.gap(body,26);body.addView(Ui.section(this,"导出与分享"));TextView export=Ui.button(this,"导出最新库存清单",true);export.setOnClickListener(v->chooseExport());body.addView(export);Ui.gap(body,12);TextView note=Ui.text(this,"CSV 清单包含名称、分类、标签、数量、单位、库存状态及更新时间，可用 Excel 打开。清单不包含照片。\n\n照片和库存仅保存在此设备；卸载应用会删除本地记录。",12,Ui.MUTED);note.setLineSpacing(d(5),1);body.addView(note);
    }
    private void chooseExport(){if(exportBusy){toast("正在准备清单…");return;}load();List<InventoryItem> filtered=InventoryQuery.filter(items,search,category,selectedTags,status,sort);String[] options={"全部库存（"+items.size()+" 种）","当前筛选结果（"+filtered.size()+" 种）"};new AlertDialog.Builder(this).setTitle("导出库存清单").setItems(options,(d,which)->{List<InventoryItem> snapshot=which==0?new ArrayList<>(items):new ArrayList<>(filtered);if(snapshot.isEmpty()){toast("没有可导出的物品");return;}new AlertDialog.Builder(this).setTitle("导出 "+snapshot.size()+" 种物品").setItems(new String[]{"保存 CSV 文件","分享 CSV 清单"},(dialog,choice)->export(snapshot,choice==1)).setNegativeButton("取消",null).show();}).setNegativeButton("取消",null).show();}
    private void export(List<InventoryItem> snapshot,boolean share){exportBusy=true;toast("正在生成最新清单…");files.execute(()->{try{File file=ExportFiles.writeCsv(this,CsvExporter.create(snapshot,System.currentTimeMillis()));runOnUiThread(()->{exportBusy=false;if(isDestroyed())return;try{if(share){Uri uri=ExportFiles.uri(this,file);Intent intent=new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,"物仓 · 库存清单").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("库存清单",uri));startActivity(Intent.createChooser(intent,"分享库存清单"));}else{pendingExport=file.getAbsolutePath();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,file.getName());startActivityForResult(intent,SAVE_CSV);}}catch(Exception e){error(e);}});}catch(Exception e){runOnUiThread(()->{exportBusy=false;error(e);});}});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SAVE_CSV&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingExport!=null){final Uri destination=data.getData();final File source=new File(pendingExport);pendingExport=null;files.execute(()->{try(FileInputStream input=new FileInputStream(source);OutputStream output=getContentResolver().openOutputStream(destination,"wt")){if(output==null)throw new java.io.IOException("无法打开目标文件");byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)output.write(b,0,n);runOnUiThread(()->toast("库存清单已保存"));}catch(Exception e){runOnUiThread(()->error(e));}});}else if(request==SAVE_CSV){pendingExport=null;}}
    private int d(float value){return Ui.dp(this,value);}private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}private void error(Exception e){if(isDestroyed()||isFinishing())return;new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(e.getMessage()==null?"请重试":e.getMessage()).setPositiveButton("知道了",null).show();}
    @Override protected void onDestroy(){files.shutdown();repository.close();super.onDestroy();}
}
