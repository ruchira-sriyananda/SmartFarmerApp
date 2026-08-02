package com.smartfarmers.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.Comment;
import com.smartfarmers.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {
    private List<Comment> comments;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private OnCommentInteractionListener listener;

    public interface OnCommentInteractionListener {
        void onCommentUpdated();
        void onCommentDeleted();
    }

    public CommentAdapter(List<Comment> comments, OnCommentInteractionListener listener) {
        this.comments = comments;
        this.listener = listener;
        this.supabaseAuth = new SupabaseAuthHelper();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        if (sessionManager == null) {
            sessionManager = new SessionManager(parent.getContext());
        }
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        Context context = holder.itemView.getContext();
        
        holder.tvUserName.setText(comment.getUserName() != null ? comment.getUserName() : "Anonymous");
        holder.tvContent.setText(comment.getContent());
        
        loadProfileImage(comment.getUserProfileImage(), holder.ivUser);

        // Action visibility (only for owner)
        if (sessionManager.getUserId() != null && sessionManager.getUserId().equals(comment.getUserId())) {
            holder.tvEdit.setVisibility(View.VISIBLE);
            holder.tvDelete.setVisibility(View.VISIBLE);
        } else {
            holder.tvEdit.setVisibility(View.GONE);
            holder.tvDelete.setVisibility(View.GONE);
        }

        // Reply indentation logic (keeping it just in case some exist, but won't be used for new ones)
        if (comment.getParentId() != null && !comment.getParentId().isEmpty() && !comment.getParentId().equals("null")) {
            holder.itemView.setPadding(48, 8, 0, 8);
        } else {
            holder.itemView.setPadding(0, 8, 0, 8);
        }

        holder.tvEdit.setOnClickListener(v -> showEditDialog(context, comment));
        holder.tvDelete.setOnClickListener(v -> showDeleteConfirmation(context, comment));

        // Time (simple parse)
        if (comment.getCreatedAt() != null && comment.getCreatedAt().contains("T")) {
            holder.tvTime.setText(comment.getCreatedAt().split("T")[0]);
        }
    }

    private void showDeleteConfirmation(Context context, Comment comment) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Comment")
                .setMessage("Are you sure you want to delete this comment?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    supabaseAuth.deleteComment(comment.getId(), new SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity)context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show();
                                    if (listener != null) listener.onCommentDeleted();
                                });
                            }
                        }
                        @Override
                        public void onError(String error) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity)context).runOnUiThread(() -> 
                                    Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show()
                                );
                            }
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(Context context, Comment comment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_comment, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        
        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText("Edit Comment");
        
        EditText etComment = dialogView.findViewById(R.id.etComment);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitComment);
        
        etComment.setText(comment.getContent());
        btnSubmit.setText("Update");
        
        btnSubmit.setOnClickListener(v -> {
            String newContent = etComment.getText().toString().trim();
            if (newContent.isEmpty()) {
                etComment.setError("Content cannot be empty");
                return;
            }
            
            if (comment.getId() == null || comment.getId().isEmpty()) {
                Toast.makeText(context, "Error: Comment ID not found", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }
            
            supabaseAuth.updateComment(comment.getId(), newContent, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity)context).runOnUiThread(() -> {
                            comment.setContent(newContent);
                            notifyDataSetChanged();
                            Toast.makeText(context, "Comment updated", Toast.LENGTH_SHORT).show();
                            if (listener != null) listener.onCommentUpdated();
                        });
                    }
                }
                @Override
                public void onError(String error) {
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity)context).runOnUiThread(() -> {
                            Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
            dialog.dismiss();
        });
        
        dialog.show();
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    private void loadProfileImage(String imageStr, ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(imageView.getContext())
                        .load(imageBytes)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivUser;
        TextView tvUserName, tvContent, tvDelete, tvEdit, tvTime;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUser = itemView.findViewById(R.id.ivCommentUser);
            tvUserName = itemView.findViewById(R.id.tvCommentUserName);
            tvContent = itemView.findViewById(R.id.tvCommentContent);
            tvDelete = itemView.findViewById(R.id.tvCommentDelete);
            tvEdit = itemView.findViewById(R.id.tvCommentEdit);
            tvTime = itemView.findViewById(R.id.tvCommentTime);
        }
    }
}
