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
    private boolean paused = true;
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

    private ArrayList <String[]> listaDeMusicas = new ArrayList<>();
    private ArrayList <Song> listaDeSons = new ArrayList<>();
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
        ActionListener buttonListenerShuffle = e -> aux();
        ActionListener buttonListenerPrevious = e -> previous();
        ActionListener buttonListenerPlayPause = e -> playPause();
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> next();
        ActionListener buttonListenerRepeat = e -> aux();

        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
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

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
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
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
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
                listaDeMusicas.remove(removeIndex);
                listaDeSons.remove(removeIndex);
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

    public void aux(){

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
                    int actualtime = (int) (counter * currentSong.getMsPerFrame());
                    int totaltime = (int) currentSong.getMsLength();
                    window.setTime(actualtime, totaltime);
                    go = playNextFrame();
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
            else if(!go && actualIndex == listaDeSons.size() - 1){
                stop();
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
        isPlaying = false;
        window.updatePlayPauseButtonIcon(paused);
        if(paused == false) {
            playingMusic();
        }
    }

    public void next() {
        if(actualIndex != listaDeSons.size()-1){
            actualIndex++;
            currentSong = listaDeSons.get(actualIndex);
            playNow(actualIndex);
        }
    }

    public void previous() {
        if(actualIndex != 0){
            actualIndex--;
            currentSong = listaDeSons.get(actualIndex);
            playNow(actualIndex);
        }
    }


    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>

}
