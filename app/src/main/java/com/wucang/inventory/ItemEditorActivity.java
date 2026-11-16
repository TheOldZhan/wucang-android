package com.wucang.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wucang.inventory.data.InventoryItem;
import com.wucang.inventory.data.InventoryRepository;
import com.wucang.inventory.io.PhotoStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItemEditorActivity extends Activity {
    private static final int CAMERA=51,GALLERY=52;
    private InventoryRepository repository;
    private PhotoStore photos;
    private InventoryItem original,draft;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText name,quantity,threshold,unit,location,note;
    private TextView categoryButton,tagsButton,saveButton,photoHint;
    private FrameLayout photoFrame;
    private File cameraFile;
    private String pendingImport;
    private boolean importing=false,finished=false;
    private boolean resumed=false,importReady=false;
    private String importedPhoto;
    private Uri importUri;
    private Exception importFailure;

    @Override protected void onCreate(Bundle state){super.onCreate(state);repository=new InventoryRepository(this);photos=new PhotoStore(this);long id=getIntent().getLongExtra("item_id",0);original=id==0?new InventoryItem():repository.getItem(id);if(original==null){toast("此物品已被删除");finish();return;}if(id==0)original.quantity=1;draft=original.copy();
        if(state!=null){draft.name=state.getString("name",draft.name);draft.quantity=state.getInt("quantity",draft.quantity);draft.lowStockThreshold=state.getInt("threshold",draft.lowStockThreshold);draft.unit=state.getString("unit",draft.unit);draft.category=state.getString("category",draft.category);draft.location=state.getString("location",draft.location);draft.note=state.getString("note",draft.note);draft.photoPath=state.getString("photo",draft.photoPath);ArrayList<String> tags=state.getStringArrayList("tags");if(tags!=null)draft.tags=tags;String camera=state.getString("camera");if(camera!=null)cameraFile=new File(camera);pendingImport=state.getString("import");}
        build();if(state!=null){quantity.setText(state.getString("rawQuantity",String.valueOf(draft.quantity)));threshold.setText(state.getString("rawThreshold",String.valueOf(draft.lowStockThreshold)));}
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::leave);
        if(pendingImport!=null)importPhoto(Uri.parse(pendingImport));}
    private void build(){
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.insets(this,root);setContentView(root);
        LinearLayout toolbar=Ui.row(this);Ui.pad(toolbar,8,8,16,8);FrameLayout back=new FrameLayout(this);back.addView(Ui.icon(this,"back",Ui.INK),new FrameLayout.LayoutParams(d(22),d(22),Gravity.CENTER));back.setContentDescription("返回库存");back.setOnClickListener(v->leave());toolbar.addView(back,new LinearLayout.LayoutParams(d(48),d(48)));toolbar.addView(Ui.bold(this,draft.id==0?"新建物品":"编辑物品",20,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));saveButton=Ui.button(this,"保存",true);saveButton.setOnClickListener(v->save());toolbar.addView(saveButton,new LinearLayout.LayoutParams(d(76),d(44)));root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=Ui.column(this);Ui.pad(body,20,12,20,28);scroll.addView(body);
        photoFrame=new FrameLayout(this);photoFrame.setBackground(Ui.outline(this,Ui.WHITE));photoFrame.setClipToOutline(true);body.addView(photoFrame,new LinearLayout.LayoutParams(-1,d(206)));renderPhoto();Ui.gap(body,12);
        LinearLayout actions=Ui.row(this);TextView camera=Ui.button(this,"拍照",false);camera.setContentDescription("拍摄物品照片");camera.setOnClickListener(v->takePhoto());TextView gallery=Ui.button(this,"从相册选择",false);gallery.setOnClickListener(v->pickPhoto());LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,d(48),1);left.setMargins(0,0,d(10),0);actions.addView(camera,left);actions.addView(gallery,new LinearLayout.LayoutParams(0,d(48),1));body.addView(actions);
        Ui.gap(body,24);body.addView(Ui.section(this,"基本信息"));name=field(body,"物品名称 *","给物品取个名字",120,R.id.item_name);name.setText(draft.name);
        label(body,"分类");categoryButton=Ui.button(this,"",false);categoryButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);categoryButton.setBackground(Ui.outline(this,Ui.WHITE));categoryButton.setOnClickListener(v->chooseCategory());body.addView(categoryButton);Ui.gap(body,16);refreshCategory();
        LinearLayout numbers=Ui.row(this);numbers.setGravity(Gravity.TOP);LinearLayout quantityCol=Ui.column(this),unitCol=Ui.column(this);LinearLayout.LayoutParams wide=new LinearLayout.LayoutParams(0,-2,2);wide.setMargins(0,0,d(12),0);numbers.addView(quantityCol,wide);numbers.addView(unitCol,new LinearLayout.LayoutParams(0,-2,1));quantity=field(quantityCol,"库存数量 *","0",9,R.id.item_quantity);quantity.setInputType(InputType.TYPE_CLASS_NUMBER);quantity.setText(String.valueOf(draft.quantity));unit=field(unitCol,"单位","件",20,R.id.item_unit);unit.setText(draft.unit);body.addView(numbers);
        threshold=field(body,"低库存提醒阈值","1",9,R.id.item_threshold);threshold.setInputType(InputType.TYPE_CLASS_NUMBER);threshold.setText(String.valueOf(draft.lowStockThreshold));TextView tip=Ui.text(this,"数量为 0 时显示已用尽；不高于阈值时提示库存不足。",11,Ui.MUTED);tip.setLineSpacing(d(3),1);body.addView(tip);Ui.gap(body,24);
        body.addView(Ui.section(this,"标签与收纳"));label(body,"标签");tagsButton=Ui.button(this,"",false);tagsButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);tagsButton.setBackground(Ui.outline(this,Ui.WHITE));tagsButton.setOnClickListener(v->chooseTags());body.addView(tagsButton);Ui.gap(body,16);refreshTags();location=field(body,"存放位置","例如：书房 · 第二层抽屉",120,R.id.item_location);location.setText(draft.location);note=field(body,"备注","规格、用途或其他想记住的信息",10000,R.id.item_note);note.setSingleLine(false);note.setMinLines(3);note.setGravity(Gravity.TOP);note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);note.setText(draft.note);
        if(draft.id>0){Ui.gap(body,14);TextView delete=Ui.button(this,"删除物品",false);delete.setTextColor(Ui.RED);delete.setOnClickListener(v->delete());body.addView(delete);}Ui.gap(body,8);
    }
    private EditText field(LinearLayout parent,String title,String hint,int max,int id){label(parent,title);EditText input=Ui.input(this,hint);input.setId(id);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});input.setContentDescription(title);parent.addView(input,new LinearLayout.LayoutParams(-1,-2));Ui.gap(parent,16);return input;}
    private void label(LinearLayout parent,String text){TextView label=Ui.bold(this,text,13,Ui.INK);Ui.pad(label,0,0,0,8);parent.addView(label);}
    private void renderPhoto(){photoFrame.removeAllViews();if(draft.photoPath.isEmpty()){LinearLayout hint=Ui.column(this);hint.setGravity(Gravity.CENTER);hint.addView(Ui.icon(this,"camera",Ui.ACCENT),new LinearLayout.LayoutParams(d(40),d(40)));Ui.gap(hint,16);TextView photoTitle=Ui.bold(this,"给物品留个影",17,Ui.INK);photoTitle.setGravity(Gravity.CENTER);hint.addView(photoTitle);Ui.gap(hint,8);TextView photoSubtitle=Ui.text(this,"一眼找到，轻松管理",12,Ui.MUTED);photoSubtitle.setGravity(Gravity.CENTER);hint.addView(photoSubtitle);photoFrame.addView(hint,new FrameLayout.LayoutParams(-1,-1));}else{ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("物品照片预览");photoFrame.addView(image,new FrameLayout.LayoutParams(-1,-1));String path=draft.photoPath;worker.execute(()->{Bitmap bitmap=photos.loadThumbnail(path,d(560));runOnUiThread(()->{if(!isDestroyed()&&path.equals(draft.photoPath))image.setImageBitmap(bitmap);else if(bitmap!=null)bitmap.recycle();});});TextView remove=Ui.chip(this,"移除照片",false);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,d(40),Gravity.BOTTOM|Gravity.END);lp.setMargins(d(12),d(12),d(12),d(12));photoFrame.addView(remove,lp);remove.setOnClickListener(v->{if(importing)return;String old=draft.photoPath;draft.photoPath="";if(!old.equals(original.photoPath))photos.deletePhoto(old);renderPhoto();});}
        photoHint=Ui.bold(this,"正在保存照片…",14,Ui.INK);photoHint.setGravity(Gravity.CENTER);photoHint.setBackgroundColor(0xEFFFFFFF);photoHint.setVisibility(importing?View.VISIBLE:View.GONE);photoFrame.addView(photoHint,new FrameLayout.LayoutParams(-1,-1));}
    private void refreshCategory(){categoryButton.setText((draft.category.isEmpty()?"未分类":draft.category)+"   ›");}
    private void refreshTags(){tagsButton.setText(draft.tags.isEmpty()?"＋  添加标签":"# "+android.text.TextUtils.join("   # ",draft.tags));}
    private void chooseCategory(){List<String> names=new ArrayList<>();names.add("未分类");names.addAll(repository.getCategories());names.add("＋ 新建分类");new AlertDialog.Builder(this).setTitle("选择分类").setItems(names.toArray(new String[0]),(dialog,which)->{if(which==names.size()-1)newName(false);else{draft.category=which==0?"":names.get(which);refreshCategory();}}).setNegativeButton("取消",null).show();}
    private void chooseTags(){List<String> tags=repository.getTags();boolean[] checked=new boolean[tags.size()];List<String> selected=new ArrayList<>(draft.tags);for(int i=0;i<tags.size();i++)checked[i]=selected.contains(tags.get(i));new AlertDialog.Builder(this).setTitle("物品标签（可多选）").setMultiChoiceItems(tags.toArray(new String[0]),checked,(d,w,on)->{if(on){if(!selected.contains(tags.get(w)))selected.add(tags.get(w));}else selected.remove(tags.get(w));}).setPositiveButton("完成",(d,w)->{draft.tags=selected;refreshTags();}).setNeutralButton("新建标签",(d,w)->{draft.tags=selected;refreshTags();newName(true);}).setNegativeButton("取消",null).show();}
    private void newName(boolean tags){EditText input=Ui.input(this,tags?"例如：常用":"例如：摄影器材");input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});LinearLayout wrap=Ui.column(this);Ui.pad(wrap,24,8,24,0);wrap.addView(input);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("新建"+(tags?"标签":"分类")).setView(wrap).setNegativeButton("取消",null).setPositiveButton("添加",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String value=input.getText().toString().trim();try{if(tags){repository.addTag(value);if(!draft.tags.contains(value))draft.tags.add(value);refreshTags();}else{repository.addCategory(value);draft.category=value;refreshCategory();}dialog.dismiss();}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();}
    private void takePhoto(){if(importing){toast("请等待照片处理完成");return;}try{cameraFile=photos.newCameraFile();Uri output=photos.cameraUri(cameraFile);Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,output).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("物品照片",output));startActivityForResult(intent,CAMERA);}catch(Exception e){photos.cleanupCameraFile(cameraFile);cameraFile=null;error("无法打开相机，请检查是否安装了相机应用，或改用相册。",e);}}
    private void pickPhoto(){if(importing){toast("请等待照片处理完成");return;}try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,GALLERY);}catch(Exception e){error("无法打开系统相册",e);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CAMERA){if(result==RESULT_OK&&cameraFile!=null&&cameraFile.length()>0)importPhoto(photos.cameraUri(cameraFile));else{photos.cleanupCameraFile(cameraFile);cameraFile=null;if(result==RESULT_OK)toast("相机未返回照片，请重试");}}else if(request==GALLERY&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}importPhoto(uri);}}
    private void importPhoto(Uri uri){pendingImport=uri.toString();importUri=uri;importing=true;importReady=false;importedPhoto=null;importFailure=null;saveButton.setEnabled(false);saveButton.setAlpha(.5f);photoHint.setVisibility(View.VISIBLE);worker.execute(()->{try{String path=photos.importPhoto(uri);runOnUiThread(()->{if(isDestroyed()||finished){photos.deletePhoto(path);return;}importedPhoto=path;importReady=true;applyImportWhenResumed();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||finished)return;importFailure=e;importReady=true;applyImportWhenResumed();});}});}
    // A stopped Activity may already have saved pendingImport. Keep its source available
    // until resume so process recreation can retry that URI without losing the photo.
    private void applyImportWhenResumed(){if(!resumed||!importReady||isDestroyed()||finished)return;String path=importedPhoto;Exception failure=importFailure;importedPhoto=null;importFailure=null;importReady=false;if(failure==null){String previous=draft.photoPath;draft.photoPath=path;if(!previous.equals(original.photoPath))photos.deletePhoto(previous);}completeImport(importUri);importUri=null;renderPhoto();if(failure!=null)error("照片处理失败",failure);}
    private void completeImport(Uri uri){importing=false;pendingImport=null;saveButton.setEnabled(true);saveButton.setAlpha(1);photoHint.setVisibility(View.GONE);photos.cleanupCameraFile(cameraFile);cameraFile=null;try{getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}}
    private void collect(){draft.name=name.getText().toString();draft.unit=unit.getText().toString();draft.location=location.getText().toString();draft.note=note.getText().toString();}
    private int number(EditText field,String title){try{long value=Long.parseLong(field.getText().toString().trim());if(value<0||value>999999999)throw new NumberFormatException();return (int)value;}catch(NumberFormatException e){field.setError("请输入 0～999999999 的整数");field.requestFocus();throw new IllegalArgumentException(title+"须为 0～999999999 的整数");}}
    private void save(){if(importing){toast("请等待照片处理完成");return;}try{collect();if(draft.name.trim().isEmpty()){name.setError("请填写物品名称");name.requestFocus();return;}draft.quantity=number(quantity,"库存数量");draft.lowStockThreshold=number(threshold,"提醒阈值");repository.saveItem(draft);if(!Objects.equals(original.photoPath,draft.photoPath))photos.deletePhoto(original.photoPath);finished=true;toast("物品已保存");finish();}catch(Exception e){error("保存失败",e);}}
    private void delete(){if(importing){toast("请等待照片处理完成");return;}new AlertDialog.Builder(this).setTitle("删除「"+original.name+"」？").setMessage("此物品的记录和保存在应用内的照片将一并删除。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{try{repository.deleteItem(original.id);photos.deletePhoto(original.photoPath);if(!Objects.equals(original.photoPath,draft.photoPath))photos.deletePhoto(draft.photoPath);finished=true;toast("物品已删除");finish();}catch(Exception e){error("删除失败",e);}}).show();}
    private boolean changed(){collect();return !Objects.equals(draft.name,original.name)||!Objects.equals(draft.unit,original.unit)||!Objects.equals(draft.location,original.location)||!Objects.equals(draft.note,original.note)||!Objects.equals(draft.photoPath,original.photoPath)||!Objects.equals(draft.category,original.category)||!new java.util.HashSet<>(draft.tags).equals(new java.util.HashSet<>(original.tags))||!quantity.getText().toString().equals(String.valueOf(original.quantity))||!threshold.getText().toString().equals(String.valueOf(original.lowStockThreshold));}
    private void leave(){if(importing){toast("正在保存照片，请稍候");return;}if(changed())new AlertDialog.Builder(this).setTitle("放弃本次编辑？").setMessage("未保存的修改将被丢弃。").setNegativeButton("继续编辑",null).setPositiveButton("放弃",(d,w)->discard()).show();else discard();}
    private void discard(){if(!Objects.equals(draft.photoPath,original.photoPath))photos.deletePhoto(draft.photoPath);photos.cleanupCameraFile(cameraFile);finished=true;finish();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onResume(){super.onResume();resumed=true;applyImportWhenResumed();}
    @Override protected void onPause(){resumed=false;super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(draft==null||name==null)return;collect();out.putString("name",draft.name);out.putInt("quantity",draft.quantity);out.putInt("threshold",draft.lowStockThreshold);out.putString("rawQuantity",quantity.getText().toString());out.putString("rawThreshold",threshold.getText().toString());out.putString("unit",draft.unit);out.putString("category",draft.category);out.putString("location",draft.location);out.putString("note",draft.note);out.putString("photo",draft.photoPath);out.putStringArrayList("tags",new ArrayList<>(draft.tags));out.putString("camera",cameraFile==null?null:cameraFile.getAbsolutePath());out.putString("import",pendingImport);}
    private int d(float value){return Ui.dp(this,value);}private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}private void error(String title,Exception e){if(isDestroyed()||isFinishing())return;new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage()==null?"请重试":e.getMessage()).setPositiveButton("知道了",null).show();}
    @Override protected void onDestroy(){if(importedPhoto!=null)photos.deletePhoto(importedPhoto);worker.shutdown();if(repository!=null)repository.close();super.onDestroy();}
}
