import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;

import java.awt.event.*;

import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;
import support.CustomFileChooser;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class Player {

    //private PlayerWindow playerWindow;
    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice the audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;


    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean playerPaused = true;
    private boolean isPlaying = false;
    private boolean paused = false; // Mudei pra começar como false pro shuffle funcionar quando não tiver começado a tocar ainda
    private boolean doublePlay = false;
    private boolean go = true;
    private Song currentSong;
    private Song selected;
    private int actualIndex;
    private int counter = 0;
    private Song newSong;
    private int currentFrame = 0;
    private int newFrame;
    private final Lock lock = new ReentrantLock();
    private int goToTime;
    private int actualTime;
    private int totalTime;

    private ArrayList <String[]> listaDeMusicas = new ArrayList<>();
    private ArrayList <Song> listaDeSons = new ArrayList<>();


    private ArrayList <Song> shuffleListSong = new ArrayList<>();
    private ArrayList <String[]> shuffleList = new ArrayList<>();
    int shuffleIndex;
    private Song shuffleSong;
    private String[] shuffleString;

    String[][] queue = {};

    public Player() {
        ActionListener buttonListenerPlayNow = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actualIndex = window.selectIndex();
                playNow(actualIndex);
            }
        };
        ActionListener buttonListenerRemove = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeFromQueue();
            }
        };
        ActionListener buttonListenerAddQueue = e -> addToQueue(newSong);
        ActionListener buttonListenerShuffle = e -> shuflleButton();
        ActionListener buttonListenerPrevious = e -> previous();
        ActionListener buttonListenerPlayPause = e -> playPause();
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> next();
        ActionListener buttonListenerRepeat = e -> repeatButton();

        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                released();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        };

        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        };

        this.window = new PlayerWindow("Spotify", this.queue, buttonListenerPlayNow,
                buttonListenerRemove, buttonListenerAddQueue, buttonListenerShuffle,
                buttonListenerPrevious, buttonListenerPlayPause, buttonListenerStop,
                buttonListenerNext, buttonListenerRepeat, scrubberListenerClick, scrubberListenerMotion);
    }

    //<editor-fold desc="Essential">


    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?

        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;
            try {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                device.write(output.getBuffer(), 0, output.getBufferLength());
                bitstream.closeFrame();
            } catch (DecoderException e){
                next();
            }
        }
        return true;
    }
    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException
     */
    private void skipToFrame(int newFrame) throws BitstreamException {

        if (newFrame >= currentFrame) {
            int framesToSkip = newFrame - currentFrame;

            boolean condition = true;
            while (framesToSkip-- >= 0 && condition)
                try {
                    condition = skipNextFrame();
                } catch (BitstreamException e) {
                    System.out.println(e);
                }
        }

    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    public void addToQueue(Song song) {
        Thread addThread = new Thread(() -> {
                try{
                    lock.lock();
                    newSong = window.getNewSong();
                    String[] metaDados = newSong.getDisplayInfo();


                    listaDeMusicas.add(metaDados);
                    listaDeSons.add(newSong);
                    shuffleList.add(metaDados);
                    shuffleListSong.add(newSong);
                    changeQueue();
                }
                catch (java.io.IOException | BitstreamException | UnsupportedTagException | InvalidDataException exception){
                    System.out.println("error");
                }
                finally {
                    lock.unlock();
                }
        });
        addThread.start();
    }

    public void removeFromQueue() {
        Thread removeThread = new Thread(() -> {
            try{

                int removeIndex;
                lock.lock();

                removeIndex = window.selectIndex();

                Song removed = listaDeSons.get(removeIndex);
                listaDeMusicas.remove(removeIndex);
                listaDeSons.remove(removeIndex);
                for (int i = 0; i < listaDeSons.size(); i++) {
                    if (removed == shuffleListSong.get(i)) {
                        shuffleListSong.remove(i);
                        shuffleList.remove(i);
                        break;
                    }
                }


                if(removeIndex == actualIndex){
                    stop();
                }
                else if(removeIndex < actualIndex){
                    actualIndex--;
                }

                changeQueue();
            }
            finally {
                lock.unlock();
            }
        });
        removeThread.start();
    }


    public void changeQueue(){
        String[][] converter = new String[this.listaDeMusicas.size()][7]; //cria uma matriz, onde cada linha representa uma musica, e tem 7 colunas que são as categorias da musica
        this.queue = this.listaDeMusicas.toArray(converter); //Converter o array list em uma matriz de array e atualizar a fila
        window.updateQueueList(this.queue); // Bota a fila na tela

    }

    public String[][] getQueueAsArray() {
        return null;
    }

    //</editor-fold>

    //<editor-fold desc="Controls">
    public void playNow(int index) {
        counter = 0;
        currentFrame = 0;

        if(isPlaying){
            doublePlay = true;
        }

        Thread playing = new Thread(() -> {
          try {

              lock.lock();
              isPlaying = true;
              paused = false;

              currentSong = listaDeSons.get(index);

              window.updatePlayingSongInfo(currentSong.getTitle(), currentSong.getAlbum(), currentSong.getArtist());
              window.updatePlayPauseButtonIcon(paused);
              window.setEnabledScrubberArea(isPlaying);


              try {
                  device = FactoryRegistry.systemRegistry().createAudioDevice();
                  device.open(decoder = new Decoder());
                  bitstream = new Bitstream(currentSong.getBufferedInputStream());
                  playingMusic();

              }
              catch (JavaLayerException | FileNotFoundException e){
                  System.out.println(e);
              }
          }
          finally {

              lock.unlock();
          }
        });
        playing.start();
    }


    public void playingMusic(){
        Thread running = new Thread(() -> {
            go = true;

            while (go && !paused) {
                try {
                    if(doublePlay){
                        doublePlay = false;
                        break;
                    }
                    actualTime = (int) (counter * currentSong.getMsPerFrame());
                    totalTime = (int) currentSong.getMsLength();
                    window.setTime(actualTime, totalTime);
                    if(window.getScrubberValue() < currentSong.getMsLength()){
                        go = playNextFrame();}
                    else{
                        go = false;
                    }
                    counter++;
                } catch (JavaLayerException e) {
                    System.out.println(e);
                }
            }
            if(!go) {
                isPlaying = false;
            }
            if(!go && actualIndex < listaDeSons.size() - 1){
                next();
            }
            else if(!go && actualIndex == listaDeSons.size() - 1 && !repeat){
                stop();
            }
            else if(!go && actualIndex == listaDeSons.size() - 1 && repeat){
                actualIndex = 0;
                playNow(actualIndex);
            }
        });
        running.start();
    }

    public void stop() {
        counter = 0;
        isPlaying = false;
        paused = true;
        window.resetMiniPlayer();
    }

    public void playPause() {
        paused = !paused;
        if(paused) {
            isPlaying = false;
        }else{
            isPlaying = true;
        }
        window.updatePlayPauseButtonIcon(paused);
        if(!paused) {
            playingMusic();
        }
    }

    public void next() {
        if(actualIndex != listaDeSons.size()-1){
            actualIndex++;
            playNow(actualIndex);
        }
        else if(actualIndex == listaDeSons.size()-1 && repeat){
            actualIndex = 0;
            playNow(actualIndex);
        }
    }

    public void previous() {
        if(actualIndex != 0){
            actualIndex--;
            playNow(actualIndex);
        }
    }

    public void repeatButton(){
        repeat = !repeat;
    }

    public void pressed(){
        paused = true;
    }

    public void released(){

            try {
                currentFrame = 0;
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(currentSong.getBufferedInputStream());

            }
            catch (JavaLayerException | FileNotFoundException e){
                System.out.println(e);
            }

            goToTime = (int) (window.getScrubberValue() / currentSong.getMsPerFrame());

            counter = goToTime;

            window.setTime((int) (counter * currentSong.getMsPerFrame()), totalTime);

            try {
                skipToFrame(goToTime);

            } catch (BitstreamException e){
                System.out.println(e);
            }

            if(isPlaying){
                paused = false;
            }

            playingMusic();

    }

    public void shuflleButton(){
        Thread random = new Thread(() -> {

            shuffle = !shuffle;

            if(isPlaying || paused) {
                if (shuffle) {

                    shuffleList.clear();
                    shuffleListSong.clear();
                    shuffleListSong.addAll(listaDeSons);
                    shuffleList.addAll(listaDeMusicas);


                    //Troca no array de Songs
                    shuffleSong = listaDeSons.get(actualIndex);
                    listaDeSons.set(actualIndex, listaDeSons.get(0));
                    listaDeSons.set(0, shuffleSong);

                    //Troca no array de Strings
                    shuffleString = listaDeMusicas.get(actualIndex);
                    listaDeMusicas.set(actualIndex, listaDeMusicas.get(0));
                    listaDeMusicas.set(0, shuffleString);

                    actualIndex = 0;

                    for (int i = 1; i < listaDeSons.size(); i++) {
                        int randomNumber = (int) Math.floor(Math.random() * (listaDeSons.size() - i) + i);
                        shuffleSong = listaDeSons.get(randomNumber);
                        listaDeSons.set(randomNumber, listaDeSons.get(i));
                        listaDeSons.set(i, shuffleSong);

                        shuffleString = listaDeMusicas.get(randomNumber);
                        listaDeMusicas.set(randomNumber, listaDeMusicas.get(i));
                        listaDeMusicas.set(i, shuffleString);
                    }

                    changeQueue();
                } else {

                    listaDeSons.clear();
                    listaDeMusicas.clear();
                    listaDeSons.addAll(shuffleListSong);
                    listaDeMusicas.addAll(shuffleList);

                    for (int i = 0; i < listaDeSons.size(); i++) {
                        if (currentSong == listaDeSons.get(i)) {
                            actualIndex = i;
                            break;
                        }
                    }
                    changeQueue();
                }
            }
            else{

                if (shuffle) {

                    shuffleList.clear();
                    shuffleListSong.clear();
                    shuffleListSong.addAll(listaDeSons);
                    shuffleList.addAll(listaDeMusicas);

                    for (int i = 0; i < listaDeSons.size(); i++) {
                        int randomNumber = (int) Math.floor(Math.random() * (listaDeSons.size() - i) + i);
                        shuffleSong = listaDeSons.get(randomNumber);
                        listaDeSons.set(randomNumber, listaDeSons.get(i));
                        listaDeSons.set(i, shuffleSong);

                        shuffleString = listaDeMusicas.get(randomNumber);
                        listaDeMusicas.set(randomNumber, listaDeMusicas.get(i));
                        listaDeMusicas.set(i, shuffleString);
                    }

                    changeQueue();
                } else {

                    listaDeSons.clear();
                    listaDeMusicas.clear();
                    listaDeSons.addAll(shuffleListSong);
                    listaDeMusicas.addAll(shuffleList);

                    for (int i = 0; i < listaDeSons.size(); i++) {
                        if (currentSong == listaDeSons.get(i)) {
                            actualIndex = i;
                            break;
                        }
                    }
                    changeQueue();
                }

            }

        });
        random.start();
    }


    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>

}
