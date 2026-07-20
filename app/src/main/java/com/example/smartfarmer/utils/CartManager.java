package com.example.smartfarmer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.smartfarmer.models.Product;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CartManager {
    private static final String PREF_NAME = "SmartFarmerCart";
    private static final String KEY_CART = "cart_items";
    private SharedPreferences prefs;

    public CartManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void addToCart(Product product) {
        List<Product> cart = getCartItems();
        for (Product p : cart) {
            if (p.getId().equals(product.getId())) return;
        }
        cart.add(product);
        saveCart(cart);
    }

    public List<Product> getCartItems() {
        List<Product> list = new ArrayList<>();
        String json = prefs.getString(KEY_CART, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Product p = new Product();
                p.setId(obj.getString("id"));
                p.setName(obj.getString("name"));
                p.setPrice(obj.getDouble("price"));
                p.setImageUrl(obj.optString("image_url", ""));
                p.setCategory(obj.optString("category", ""));
                p.setDescription(obj.optString("description", ""));
                p.setSellerId(obj.optString("seller_id", ""));
                list.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void removeFromCart(String productId) {
        List<Product> cart = getCartItems();
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).getId().equals(productId)) {
                cart.remove(i);
                break;
            }
        }
        saveCart(cart);
    }

    public void clearCart() {
        prefs.edit().remove(KEY_CART).apply();
    }

    public int getCartCount() {
        return getCartItems().size();
    }

    private void saveCart(List<Product> cart) {
        try {
            JSONArray arr = new JSONArray();
            for (Product p : cart) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.getId());
                obj.put("name", p.getName());
                obj.put("price", p.getPrice());
                obj.put("image_url", p.getImageUrl());
                obj.put("category", p.getCategory());
                obj.put("description", p.getDescription());
                obj.put("seller_id", p.getSellerId());
                arr.put(obj);
            }
            prefs.edit().putString(KEY_CART, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
