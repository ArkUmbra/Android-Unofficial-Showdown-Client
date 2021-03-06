package com.majeur.psclient.io;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.target.ViewTarget;
import com.majeur.psclient.R;
import com.majeur.psclient.model.battle.Player;
import com.majeur.psclient.model.pokemon.BasePokemon;
import com.majeur.psclient.model.pokemon.BattlingPokemon;
import com.majeur.psclient.util.Utils;
import com.majeur.psclient.util.html.Html;

import java.util.concurrent.ExecutionException;

import static com.majeur.psclient.model.battle.Player.FOE;

public class GlideHelper {

    static {
        ViewTarget.setTagId(R.id.glide_tag);
    }

    private final RequestManager mRequestManager;

    public GlideHelper(Context context) {
        mRequestManager = Glide.with(context);
    }

    private final float MAGIC_SCALE = 0.0027777777777778f;

    @SuppressWarnings("CheckResult")
    public void loadSprite(final BattlingPokemon pokemon, final ImageView imageView, final int fieldWidth) {
        String spriteId = pokemon.getTransformSpecies() != null ? pokemon.getTransformSpecies() : pokemon.getSpriteId();
        RequestBuilder<Drawable> request = mRequestManager.load(ani3dSpriteUri(spriteId, pokemon.getFoe(), pokemon.getShiny()));
        RequestOptions options = new RequestOptions().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        request.apply(options);
        request.error(
                mRequestManager.load(ani2dSpriteUri(spriteId, pokemon.getFoe(), pokemon.getShiny()))
                .error(
                        mRequestManager.load(fixed2dSpriteUri(spriteId, pokemon.getFoe(), pokemon.getShiny()))
                        .apply(options.error(R.drawable.missingno))
                )
        );
        request.into(new AnimatedImageViewTarget(imageView) {
            @Override
            protected void onInitInAnimation(ViewPropertyAnimator viewPropertyAnimator) {
                viewPropertyAnimator
                        .setDuration(250)
                        .setInterpolator(new DecelerateInterpolator())
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f);
            }

            @Override
            protected void onInitOutAnimation(ViewPropertyAnimator viewPropertyAnimator) {
                viewPropertyAnimator
                        .setDuration(250)
                        .setInterpolator(new AccelerateInterpolator())
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f);
            }

            @Override
            protected void setResource(Drawable resource) {
                int scale = Math.round(fieldWidth * MAGIC_SCALE);
                if (!pokemon.getFoe()) scale = Math.round(scale * 1.5f);

                ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
                layoutParams.width = resource.getIntrinsicWidth() * scale;
                layoutParams.height = resource.getIntrinsicHeight() * scale;

                imageView.setImageDrawable(resource);
            }
        });
    }

    public void loadPreviewSprite(Player player, BasePokemon pokemon, ImageView imageView) {
        RequestBuilder<Drawable> request = mRequestManager.load(ani3dSpriteUri(pokemon.getSpriteId(), player == FOE, false));
        RequestOptions options = new RequestOptions().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        request.apply(options);
        request.error(
                mRequestManager.load(ani2dSpriteUri(pokemon.getSpriteId(), player == FOE, false))
                        .error(
                                mRequestManager.load(fixed2dSpriteUri(pokemon.getSpriteId(), player == FOE, false))
                                        .apply(options.error(R.drawable.missingno))
                        )
        );
        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
        layoutParams.width = layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        request.into(imageView);
    }

    public void loadDexSprite(BasePokemon pokemon, boolean shiny, ImageView imageView) {
        RequestBuilder<Drawable> request = mRequestManager.load(dexSpriteUri(pokemon.getSpriteId(), shiny));
        RequestOptions options = new RequestOptions().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        request.apply(options);
        request.error(
                mRequestManager.load(fixed2dSpriteUri(pokemon.getSpriteId(), true, false))
                        .apply(options.error(R.drawable.placeholder_pokeball))
        );
        request.into(imageView);
    }

    public void loadAvatar(String avatar, ImageView imageView) {
        RequestBuilder<Drawable> request = mRequestManager.load(avatarSpriteUri(avatar));
        request.into(imageView);
    }

    private StringBuilder baseUri() {
        return new StringBuilder()
                .append("https://play.pokemonshowdown.com/sprites/");
    }

    private String ani3dSpriteUri(String spriteId, boolean foe, boolean shiny) {
        return baseUri()
                .append(foe ? "ani" : "ani-back")
                .append(shiny ? "-shiny/" : "/")
                .append(spriteId)
                .append(".gif")
                .toString();
    }

    private String ani2dSpriteUri(String spriteId, boolean foe, boolean shiny) {
        return baseUri()
                .append(foe ? "gen5ani" : "gen5ani-back")
                .append(shiny ? "-shiny/" : "/")
                .append(spriteId)
                .append(".png")
                .toString();
    }

    private String fixed2dSpriteUri(String spriteId, boolean foe, boolean shiny) {
        return baseUri()
                .append(foe ? "gen5" : "gen5-back")
                .append(shiny ? "-shiny/" : "/")
                .append(spriteId)
                .append(".png")
                .toString();
    }

    private String dexSpriteUri(String spriteId, boolean shiny) {
        return baseUri()
                .append("dex")
                .append(shiny ? "-shiny/" : "/")
                .append(spriteId)
                .append(".png")
                .toString();
    }

    private String typeSpriteUri(String type) {
        return baseUri()
                .append("types/")
                .append(Utils.firstCharUpperCase(type))
                .append(".png")
                .toString();
    }

    private String categorySpriteUri(String category) {
        return baseUri()
                .append("categories/")
                .append(Utils.firstCharUpperCase(category))
                .append(".png")
                .toString();
    }

    private String avatarSpriteUri(String avatar) {
        return baseUri()
                .append("trainers/")
                .append(avatar)
                .append(".png")
                .toString();
    }

    public Html.ImageGetter getHtmlImageGetter(final AssetLoader iconLoader, int maxWidth) {
        final int mw = maxWidth - Utils.dpToPx(2);
        return new Html.ImageGetter() {
            @Override
            public Drawable getDrawable(final String source, int reqw, int reqh) {
                try {
                    Drawable d = null;
                    if (source.startsWith("content://com.majeur.psclient/dex-icon/")) {
                        String species = source.substring(source.lastIndexOf('/') + 1, source.length());
                        Bitmap icon = iconLoader.dexIconNonSuspend(species);
                        if (icon != null) d = new BitmapDrawable(icon);
                    } else {
                        d = mRequestManager.asDrawable().load(source).submit().get();
                    }
                    if (d == null) return null;
                    float r = d.getIntrinsicWidth() / (float) d.getIntrinsicHeight();
                    int w, h;
                    if (reqw != 0 && reqh == 0) {
                        w = reqw;
                        h = (int) (w / r);
                    } else if (reqw == 0 && reqh != 0) {
                        h = reqh;
                        w = (int) (h * r);
                    } else {
                        w = reqw;
                        h = reqh;
                    }
                    float mr = w / (float) mw;
                    if (mr > 1) {
                        w = mw;
                        h /= mr;
                    }
                    d.setBounds(0, 0, w, h);
                    return d;
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        };
    }
}
