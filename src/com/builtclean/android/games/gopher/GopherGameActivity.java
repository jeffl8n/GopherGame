package com.builtclean.android.games.gopher;

import java.util.ArrayList;
import java.util.Random;

import org.anddev.andengine.engine.camera.Camera;
import org.anddev.andengine.engine.options.EngineOptions;
import org.anddev.andengine.engine.options.EngineOptions.ScreenOrientation;
import org.anddev.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.anddev.andengine.entity.modifier.RotationModifier;
import org.anddev.andengine.entity.modifier.ScaleModifier;
import org.anddev.andengine.entity.primitive.Rectangle;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.scene.Scene.IOnAreaTouchListener;
import org.anddev.andengine.entity.scene.Scene.IOnSceneTouchListener;
import org.anddev.andengine.entity.scene.Scene.ITouchArea;
import org.anddev.andengine.entity.scene.background.RepeatingSpriteBackground;
import org.anddev.andengine.entity.shape.Shape;
import org.anddev.andengine.entity.sprite.AnimatedSprite;
import org.anddev.andengine.entity.text.Text;
import org.anddev.andengine.entity.util.FPSLogger;
import org.anddev.andengine.extension.physics.box2d.PhysicsConnector;
import org.anddev.andengine.extension.physics.box2d.PhysicsFactory;
import org.anddev.andengine.extension.physics.box2d.PhysicsWorld;
import org.anddev.andengine.input.touch.TouchEvent;
import org.anddev.andengine.opengl.font.Font;
import org.anddev.andengine.opengl.font.FontFactory;
import org.anddev.andengine.opengl.texture.Texture;
import org.anddev.andengine.opengl.texture.TextureOptions;
import org.anddev.andengine.opengl.texture.region.TextureRegionFactory;
import org.anddev.andengine.opengl.texture.region.TiledTextureRegion;
import org.anddev.andengine.opengl.texture.source.AssetTextureSource;
import org.anddev.andengine.sensor.accelerometer.AccelerometerData;
import org.anddev.andengine.sensor.accelerometer.IAccelerometerListener;
import org.anddev.andengine.ui.activity.BaseGameActivity;
import org.anddev.andengine.util.HorizontalAlign;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.greystripe.android.sdk.GSSDK;

public class GopherGameActivity extends BaseGameActivity implements
		IAccelerometerListener, IOnSceneTouchListener, IOnAreaTouchListener {

	private GSSDK sdk;

	private static float mCameraWidth = 480;
	private static float mCameraHeight = 720;

	private Boolean gameRunning = false;
	
	private Display display;

	private Texture mTexture;
	private Texture gTexture;
	private Texture bTexture;
	private Texture hTexture;
	private Body holeBody;
	private Body bombBody;
	private Body gopherBody;
	private AnimatedSprite holeEntity;
	private AnimatedSprite bombEntity;
	private AnimatedSprite gopherEntity;

	private Text mGameOverText;
	private Texture mFontTexture;
	private Font mFont;
	private int mScore = 0;

	private RepeatingSpriteBackground mGrassBackground;

	private TiledTextureRegion mBoxRockTextureRegion;
	private TiledTextureRegion gopherTextureRegion;
	private TiledTextureRegion bombTextureRegion;
	private TiledTextureRegion holeTextureRegion;
	private Random mRnd = new Random();

	private ArrayList<AnimatedSprite> rockArray = new ArrayList<AnimatedSprite>();

	private int powerLevel;

	private PhysicsWorld mPhysicsWorld;

	private float mGravityX;
	private float mGravityY;

	private final Vector2 mTempVector = new Vector2();
	
	private BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			int rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			int level = -1;
			if (rawlevel >= 0 && scale > 0) {
				level = (rawlevel * 100) / scale;
			}
			powerLevel = level;

			if (!gameRunning) {
				return;
			}

			getEngine().runOnUpdateThread(new Runnable() {

				@Override
				public void run() {
					updateRocks();
				}
			});
		}
	};

	@Override
	public org.anddev.andengine.engine.Engine onLoadEngine() {
		
		sdk = GSSDK.initialize(getApplicationContext(),
				"348d727a-dbff-421f-8d4f-5e4173c69e41");

		display = getWindowManager().getDefaultDisplay();

		mCameraWidth = display.getWidth();
		mCameraHeight = display.getHeight();
		
		final Camera camera = new Camera(0, 0, mCameraWidth, mCameraHeight);

		final EngineOptions engineOptions = new EngineOptions(true,
				ScreenOrientation.PORTRAIT, new RatioResolutionPolicy(mCameraWidth, mCameraHeight), camera);
		engineOptions.getTouchOptions().setRunOnUpdateThread(true);
		
		return new org.anddev.andengine.engine.Engine(engineOptions);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		cleanupGame();
		unregisterReceiver(batteryLevelReceiver);
	}

	@Override
	public void onLoadResources() {
		
		mGrassBackground = new RepeatingSpriteBackground(mCameraWidth,
				mCameraHeight, getEngine().getTextureManager(),
				new AssetTextureSource(this, "gfx/background_grass.png"));
		/* Load the font we are going to use. */
		FontFactory.setAssetBasePath("font/");
		mFontTexture = new Texture(512, 512, TextureOptions.BILINEAR);
		mFont = FontFactory.createFromAsset(mFontTexture, this, "Plok.ttf", 32,
				true, Color.WHITE);
		getEngine().getTextureManager().loadTexture(mFontTexture);
		getEngine().getFontManager().loadFont(mFont);

		mTexture = new Texture(256, 32, TextureOptions.DEFAULT);
		TextureRegionFactory.setAssetBasePath("gfx/");
		mBoxRockTextureRegion = TextureRegionFactory.createTiledFromAsset(
				mTexture, this, "blocks.png", 0, 0, 5, 1);
		getEngine().getTextureManager().loadTexture(mTexture);
		gTexture = new Texture(64, 64, TextureOptions.DEFAULT);
		gopherTextureRegion = TextureRegionFactory.createTiledFromAsset(
				gTexture, this, "gopher.png", 0, 0, 1, 1);
		getEngine().getTextureManager().loadTexture(gTexture);
		bTexture = new Texture(64, 64, TextureOptions.DEFAULT);
		bombTextureRegion = TextureRegionFactory.createTiledFromAsset(bTexture,
				this, "tnt.png", 0, 0, 1, 1);
		getEngine().getTextureManager().loadTexture(bTexture);

		hTexture = new Texture(64, 64, TextureOptions.DEFAULT);
		holeTextureRegion = TextureRegionFactory.createTiledFromAsset(hTexture,
				this, "hole.png", 0, 0, 1, 1);
		getEngine().getTextureManager().loadTexture(hTexture);
		
	}

	/**
	 * @see org.anddev.andengine.ui.IGameInterface#onLoadScene()
	 */
	@Override
	public Scene onLoadScene() {

		getEngine().registerUpdateHandler(new FPSLogger());

		final Scene scene = new Scene(2);
		scene.setBackground(mGrassBackground);

		return scene;
	}

	@Override
	public void onLoadComplete() {

		if (!gameRunning) {
			setupGame();
		}
		
	}

	private void setupGame() {
		
		if(!gameRunning)
		{

			synchronized (gameRunning) {
				
				cleanupGame();

				mPhysicsWorld = new PhysicsWorld(new Vector2(0,
						SensorManager.GRAVITY_EARTH), true);
	
				final Shape roof = new Rectangle(0, 0, 0, mCameraHeight);
				final Shape right = new Rectangle(0, 0, mCameraWidth, 0);
				final Shape ground = new Rectangle(mCameraWidth, 0, 0, mCameraHeight);
				final Shape left = new Rectangle(0, mCameraHeight, mCameraWidth, 0);
	
				final FixtureDef wallFixtureDef = PhysicsFactory.createFixtureDef(0,
						0.5f, 0.5f);
				PhysicsFactory.createBoxBody(mPhysicsWorld, roof, BodyType.StaticBody,
						wallFixtureDef);
				PhysicsFactory.createBoxBody(mPhysicsWorld, right, BodyType.StaticBody,
						wallFixtureDef);
				PhysicsFactory.createBoxBody(mPhysicsWorld, ground,
						BodyType.StaticBody, wallFixtureDef);
				PhysicsFactory.createBoxBody(mPhysicsWorld, left, BodyType.StaticBody,
						wallFixtureDef);
	
				getEngine().getScene().getFirstChild().attachChild(roof);
				getEngine().getScene().getFirstChild().attachChild(right);
				getEngine().getScene().getFirstChild().attachChild(ground);
				getEngine().getScene().getFirstChild().attachChild(left);
	
				getEngine().getScene().registerUpdateHandler(mPhysicsWorld);
	
				getEngine().getScene().setOnAreaTouchListener(this);
				getEngine().getScene().setOnSceneTouchListener(this);
				
				addHole();
				addBomb();
				addGopher();
	
				updateRocks();
	
				getEngine().getScene().getLastChild().detachChild(mGameOverText);
				
				registerReceiver(batteryLevelReceiver, new IntentFilter(
						Intent.ACTION_BATTERY_CHANGED));
				
				setUpContactChecking();
				
				gameRunning = true;
			}
		}
	}

	@Override
	public void onAccelerometerChanged(
			final AccelerometerData pAccelerometerData) {

		if (display.getRotation() == Surface.ROTATION_0) {
			mGravityX = -pAccelerometerData.getX();
			mGravityY = pAccelerometerData.getY();
		} else if (display.getRotation() == Surface.ROTATION_90) {
			mGravityX = pAccelerometerData.getY();
			mGravityY = pAccelerometerData.getX();
		} else if (display.getRotation() == Surface.ROTATION_180) {
			mGravityX = pAccelerometerData.getX();
			mGravityY = -pAccelerometerData.getY();
		} else if (display.getRotation() == Surface.ROTATION_270) {
			mGravityX = -pAccelerometerData.getY();
			mGravityY = -pAccelerometerData.getX();
		}

		mTempVector.set(mGravityX, mGravityY);

		mPhysicsWorld.setGravity(mTempVector);
	}

	private void addRock(final float pX, final float pY) {

		final Scene scene = getEngine().getScene();

		final AnimatedSprite rock;
		final Body body;

		final FixtureDef objectFixtureDef = PhysicsFactory.createFixtureDef(
				0.7f, 0.5f, 1.5f);

		rock = new AnimatedSprite(pX, pY, mBoxRockTextureRegion.clone());
		float scale = (float) Math.sqrt((mCameraWidth * mCameraHeight)
				/ (rock.getWidth() * rock.getHeight() * 100)) - 0.5f;
		rock.setScale(scale);
		rock.setRotation(mRnd.nextFloat());
		int tileIndex = mRnd.nextInt(5);
		rock.setCurrentTileIndex(tileIndex);
		body = PhysicsFactory.createBoxBody(mPhysicsWorld, rock,
				BodyType.DynamicBody, objectFixtureDef);

		scene.registerTouchArea(rock);

		scene.getLastChild().attachChild(rock);
		mPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(rock, body,
				true, true));
		rockArray.add(rock);
	}

	private void addGopher() {
		if(gopherEntity == null) 
		{
			final Scene scene = getEngine().getScene();
			final AnimatedSprite gopher;
			final Body body;
			final FixtureDef objectFixtureDef = PhysicsFactory.createFixtureDef(
					1.5f, 0.5f, 0.1f);
			int x = mRnd.nextInt((int) mCameraWidth - 64);
			int y = mRnd.nextInt((int) mCameraHeight - 64);
	
			while (Math.abs(x - holeEntity.getX()) < 100
					|| Math.abs(x - bombEntity.getX()) < 100) {
				x = mRnd.nextInt((int) mCameraWidth - 64);
			}
			while (Math.abs(y - holeEntity.getY()) < 100
					|| Math.abs(y - bombEntity.getY()) < 100) {
				y = mRnd.nextInt((int) mCameraHeight - 64);
			}
	
			gopher = new AnimatedSprite(x, y, gopherTextureRegion);
			float scale = (float) Math.sqrt((mCameraWidth * mCameraHeight)
					/ (gopher.getWidth() * gopher.getHeight() * 100)) - 0.5f;
			gopher.setScale(scale);
			body = PhysicsFactory.createCircleBody(mPhysicsWorld, gopher,
					BodyType.DynamicBody, objectFixtureDef);
			mPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(gopher,
					body, true, true));
	
			scene.registerTouchArea(gopher);
			scene.getLastChild().attachChild(gopher);
			gopherBody = body;
			gopherEntity = gopher;
		}
	}

	private void addBomb() {
		if(bombEntity == null) 
		{
			final Scene scene = getEngine().getScene();
			final AnimatedSprite bomb;
			final Body body;
			final FixtureDef objectFixtureDef = PhysicsFactory.createFixtureDef(
					1.2f, 0.5f, 0.5f);
			bomb = new AnimatedSprite(mRnd.nextInt((int) mCameraWidth - 64),
					mRnd.nextInt((int) mCameraHeight - 64), bombTextureRegion);
			float scale = (float) Math.sqrt((mCameraWidth * mCameraHeight)
					/ (bomb.getWidth() * bomb.getHeight() * 100)) + 0.25f;
			bomb.setScale(scale);
			body = PhysicsFactory.createBoxBody(mPhysicsWorld, bomb,
					BodyType.DynamicBody, objectFixtureDef);
			mPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(bomb, body,
					true, true));
	
			scene.getLastChild().attachChild(bomb);
			bombBody = body;
			bombEntity = bomb;
		}
	}

	private void addHole() {
		if(holeEntity == null)
		{
			final Scene scene = getEngine().getScene();
			final AnimatedSprite hole;
			final Body body;
			final FixtureDef objectFixtureDef = PhysicsFactory.createFixtureDef(
					1.0f, 1.0f, 1.0f);
			hole = new AnimatedSprite(mRnd.nextInt((int) mCameraWidth - 128),
					mRnd.nextInt((int) mCameraHeight - 128), holeTextureRegion);
	
			float scale = (float) Math.sqrt((mCameraWidth * mCameraHeight)
					/ (hole.getWidth() * hole.getHeight() * 100));
			hole.setScale(scale);
	
			body = PhysicsFactory.createCircleBody(mPhysicsWorld, hole,
					BodyType.StaticBody, objectFixtureDef);
			mPhysicsWorld.registerPhysicsConnector(new PhysicsConnector(hole, body,
					false, false));
	
			scene.getLastChild().attachChild(hole);
			holeBody = body;
			holeEntity = hole;
		}
	}

	private void setUpContactChecking() {
		mPhysicsWorld.setContactListener(new ContactListener() {
			@Override
			public void beginContact(Contact contact) {
				if (gameRunning) {
					checkWhatGopherIsTouching(contact.getFixtureA().getBody(),
							contact.getFixtureB().getBody());
				}
			}

			@Override
			public void endContact(Contact contact) {
			}

		});
	}

	private void checkWhatGopherIsTouching(Body bodyA, Body bodyB) {
		if (bodyA == holeBody && bodyB == gopherBody || bodyA == gopherBody
				&& bodyB == holeBody) {

			mScore += powerLevel;
			mGameOverText = new Text(0, 0, mFont, "You Win!\n\nScore:\n"
					+ powerLevel + "\nTotal Score:\n" + mScore
					+ "\n\nTap to\ntry again!", HorizontalAlign.CENTER);
			mGameOverText.setPosition(
					mCameraWidth / 2 - mGameOverText.getWidth() / 2,
					mCameraHeight / 2 - mGameOverText.getHeight() / 2);
			mGameOverText.registerEntityModifier(new ScaleModifier(3, 0.1f,
					1.25f));
			mGameOverText
					.registerEntityModifier(new RotationModifier(3, 0, 720));

			getEngine().runOnUpdateThread(new Runnable() {
				@Override
				public void run() {
					try {
						getEngine().stop();
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					} finally {
						getEngine().start();
						getEngine().getScene().getLastChild()
								.attachChild(mGameOverText);
						resetAll();
					}
				}
			});
			

		} else if (bodyB == gopherBody && bodyA == bombBody
				|| bodyB == bombBody && bodyA == gopherBody) {

			mScore -= powerLevel;
			mGameOverText = new Text(0, 0, mFont, "Game\nOver\n\nScore:\n"
					+ (powerLevel * -1) + "\nTotal Score:\n" + mScore
					+ "\n\nTap to\ntry again!", HorizontalAlign.CENTER);
			mGameOverText.setPosition(
					mCameraWidth / 2 - mGameOverText.getWidth() / 2,
					mCameraHeight / 2 - mGameOverText.getHeight() / 2);
			mGameOverText.registerEntityModifier(new ScaleModifier(3, 0.1f,
					1.25f));
			mGameOverText
					.registerEntityModifier(new RotationModifier(3, 0, 720));

			getEngine().runOnUpdateThread(new Runnable() {
				@Override
				public void run() {
					try {
						getEngine().stop();
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					} finally {
						getEngine().getScene().getLastChild()
								.attachChild(mGameOverText);
						getEngine().start();
						resetAll();
					}
				}
			});
		}
	}

	private void updateRocks() {
		int diff = powerLevel - rockArray.size();

		for (int i = 0; i < diff; i++) {

			GopherGameActivity.this.addRock(
					mRnd.nextInt((int) GopherGameActivity.mCameraWidth - 32),
					mRnd.nextInt((int) GopherGameActivity.mCameraHeight - 32));
		}
		for (int i = diff; i < 0; i++) {
			removeRock();
		}
	}

	private void removeRock() {

		if (rockArray.size() == 0)
			return;

		final AnimatedSprite rock = rockArray.remove(rockArray.size() - 1);

		removeEntity(rock);

	}

	private void removeEntity(AnimatedSprite entity) {
		final Scene scene = getEngine().getScene();

		final PhysicsConnector entityPhysicsConnector = mPhysicsWorld
				.getPhysicsConnectorManager().findPhysicsConnectorByShape(
						entity);

		mPhysicsWorld.unregisterPhysicsConnector(entityPhysicsConnector);
		mPhysicsWorld.destroyBody(entityPhysicsConnector.getBody());

		scene.unregisterTouchArea(entity);
		scene.getLastChild().detachChild(entity);
	}

	private void resetAll() {

		synchronized (gameRunning) {
			gameRunning = false;

			if(gopherEntity != null)
			{
				removeEntity(gopherEntity);
				gopherEntity = null;
				gopherBody = null;
			}
			if(holeEntity != null)
			{
				removeEntity(holeEntity);
				holeEntity = null;
				holeBody = null;
			}
			if(bombEntity != null)
			{
				removeEntity(bombEntity);
				bombEntity = null;
				bombBody = null;
			}
			int diff = rockArray.size();
			for (int i = 0; i < diff; i++) {
				removeRock();
			}
		}
	}

	@Override
	public boolean onSceneTouchEvent(Scene sceneTouched,
			TouchEvent sceneTouchEvent) {
		if (sceneTouchEvent.getAction() == MotionEvent.ACTION_DOWN) {
			if(!gameRunning) {
				sdk.displayAd(this);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onAreaTouched(final TouchEvent areaTouchEvent,
			final ITouchArea pTouchArea, final float pTouchAreaLocalX,
			final float pTouchAreaLocalY) {

		if (areaTouchEvent.getAction() == MotionEvent.ACTION_DOWN) {

			mPhysicsWorld.postRunnable(new Runnable() {
				@Override
				public void run() {
					final AnimatedSprite rock = (AnimatedSprite) pTouchArea;

					jumpRock(rock);
				}
			});
		}
		return false;
	}

	private void jumpRock(final AnimatedSprite rock) {

		final Body rockBody = mPhysicsWorld.getPhysicsConnectorManager()
				.findBodyByShape(rock);
		if (rockBody == null)
			return;
		Vector2 impulse = new Vector2(mRnd.nextFloat(), mRnd.nextFloat());
		rockBody.applyLinearImpulse(
				impulse,
				new Vector2(rockBody.getWorldCenter().x, rockBody
						.getWorldCenter().y));

		rockBody.setLinearVelocity(mTempVector.set(mGravityX * -10, mGravityY
				* -10));
	}

	private void cleanupGame() {
		
		resetAll();
		
		getEngine().getScene().clearUpdateHandlers();
		getEngine().clearUpdateHandlers();
		
	}

	@Override
	public void onUnloadResources() {
	}

	@Override
	public void onGamePaused() {

		super.onGamePaused();

		this.disableAccelerometerSensor();
		
		cleanupGame();

	}

	@Override
	public void onGameResumed() {

		super.onGameResumed();

		this.enableAccelerometerSensor(this);
		
		if (!gameRunning) {
			setupGame();
		}
	};
}
