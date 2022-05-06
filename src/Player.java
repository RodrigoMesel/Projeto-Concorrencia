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


    /**
     * Criando variáveis
     */
    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean playerPaused = true;
    private boolean isPlaying = false;
    private boolean paused = false;
    private boolean doublePlay = false;
    private boolean go = true;
    private Song currentSong;
    private int actualIndex;
    private int counter = 0;
    private Song newSong;
    private int currentFrame = 0;
    private final Lock lock = new ReentrantLock();
    private int goToTime;
    private int actualTime;
    private int totalTime;
    private ArrayList <String[]> listaDeMusicas = new ArrayList<>();
    private ArrayList <Song> listaDeSons = new ArrayList<>();
    private ArrayList <Song> shuffleListSong = new ArrayList<>();
    private ArrayList <String[]> shuffleList = new ArrayList<>();
    private Song shuffleSong;
    private String[] shuffleString;
    String[][] queue = {};

    /**
     * Classe player
     */

    public Player() {
        ActionListener buttonListenerPlayNow = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                /**
                 * Quando apertar o playnow, pega o index da musica selecionada
                 * e chamaa função playNow para começar a música
                 */
                actualIndex = window.selectIndex();
                playNow(actualIndex);
            }
        };
        ActionListener buttonListenerRemove = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                /**
                 * Quando o botão de remover for apertado, chama a função removeFromQueue
                 */
                removeFromQueue();
            }
        };

        /**
         * Quando o botão addSong for apertado, chama a função addToQueue
         */
        ActionListener buttonListenerAddQueue = e -> addToQueue(newSong);

        /**
         * Quando o botão de aleatório é apretado, chama a função shuffle
         */
        ActionListener buttonListenerShuffle = e -> shuffleButton();

        /**
         * Quando o botão de voltar é apertado, chama a função previous
         */
        ActionListener buttonListenerPrevious = e -> previous();

        /**
         * Quando o botão de play ou pause é apertado, chama a função playPause
         */
        ActionListener buttonListenerPlayPause = e -> playPause();

        /**
         * Quando o botão de stop é apertado, chama a função stop
         */
        ActionListener buttonListenerStop = e -> stop();

        /**
         * Quando o botão de next é apertado, chama a função next
         */
        ActionListener buttonListenerNext = e -> next();

        /**
         * Quando o botão de repeat é apertado, chama a função repeatButton
         */
        ActionListener buttonListenerRepeat = e -> repeatButton();

        /**
         * Declarando funções do mouse
         */

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

        /**
         * Declarando a janela
         */

        this.window = new PlayerWindow("Spotify", this.queue, buttonListenerPlayNow,
                buttonListenerRemove, buttonListenerAddQueue, buttonListenerShuffle,
                buttonListenerPrevious, buttonListenerPlayPause, buttonListenerStop,
                buttonListenerNext, buttonListenerRepeat, scrubberListenerClick, scrubberListenerMotion);
    }

    //<editor-fold desc="Essential">


    /**
     * @return False if there are no more frames to play.
     * Toca o próximo frame da música
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
     * Pula o próximo frame da música
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
     * Pula para um frame especifico da música
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

    /**
     * Adiciona uma música no final das listas que armazenam as músicas
     * @param song
     */
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

    /**
     * Função que remove uma música selecionada da lista
     */

    public void removeFromQueue() {
        Thread removeThread = new Thread(() -> {
            try{

                int removeIndex;
                lock.lock();

                removeIndex = window.selectIndex(); //Pega o index selecionado

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

    /**
     * Função que transforma a array list de musicas em um matriz de strings
     */

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

    /**
     * Função que começa a tocar uma música
     * @param index
     */
    public void playNow(int index) {
        counter = 0;
        currentFrame = 0;

        if(isPlaying){
            doublePlay = true; // para encerrar a música anterior
        }

        Thread playing = new Thread(() -> {
          try {

              lock.lock();
              isPlaying = true;
              paused = false;

              currentSong = listaDeSons.get(index); // pega o index da música selecionada

              //Atualiza as informações no player
              window.updatePlayingSongInfo(currentSong.getTitle(), currentSong.getAlbum(), currentSong.getArtist());
              window.updatePlayPauseButtonIcon(paused);
              window.setEnabledScrubberArea(isPlaying);


              try {
                  /**
                   * Cria o device, decoder e bitstream para rodar a música e chama função
                   * playingMusic, que toca a música de fato
                   */
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

    /**
     * Função que toca a música
     */

    public void playingMusic(){
        Thread running = new Thread(() -> {
            go = true;

            while (go && !paused) {
                try {
                    if(doublePlay){
                        doublePlay = false; //para evitar sobreposição de músicas
                        break;
                    }

                    actualTime = (int) (counter * currentSong.getMsPerFrame());
                    totalTime = (int) currentSong.getMsLength();
                    window.setTime(actualTime, totalTime); //Atualizar o tempo do scrubber
                    if(window.getScrubberValue() < currentSong.getMsLength()){
                        go = playNextFrame();} //Toca o próximo frame da música
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
                next(); //começa a proxima música da lista
            }
            else if(!go && actualIndex == listaDeSons.size() - 1 && !repeat){
                stop(); //Se acabou a lista e o repeat não estiver ativo, chama o stop
            }
            else if(!go && actualIndex == listaDeSons.size() - 1 && repeat){
                actualIndex = 0; //Se acabou a lista e o repeat estiver ativo, recomeça a lista
                playNow(actualIndex);
            }
        });
        running.start();
    }

    /**
     * Função que para a reprodução completamente
     */
    public void stop() {
        counter = 0;
        isPlaying = false;
        paused = true;
        window.resetMiniPlayer();
    }

    /**
     * Função que pausa ou da play na música
     */
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


    /**
     * Função que pula para a próxima música da reprodução, caso a música atual não
     * seja a última, e caso seja, só pula pra próxima se o repeat estiver ativo
     */
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

    /**
     * Função que volta uma música
     */
    public void previous() {
        if(actualIndex != 0){
            actualIndex--;
            playNow(actualIndex);
        }
    }

    /**
     * Função que ativa ou desativa o repeat
     */
    public void repeatButton(){
        repeat = !repeat;
    }


    /**
     * Quando o mouse for pressionado, pausa a música
     */
    public void pressed(){
        paused = true;
    }

    /**
     * Quando o mouse for solto, a música vai para onde o scrubber foi solto
     */
    public void released(){

            try {
                /**
                 * Recria o device, decoder e bitstream para poder voltar a música
                 * com o scrubber também
                 */
                currentFrame = 0;
                device = FactoryRegistry.systemRegistry().createAudioDevice();
                device.open(decoder = new Decoder());
                bitstream = new Bitstream(currentSong.getBufferedInputStream());

            }
            catch (JavaLayerException | FileNotFoundException e){
                System.out.println(e);
            }

            //Pega o tempo final que foi solto pelo scrubber
            goToTime = (int) (window.getScrubberValue() / currentSong.getMsPerFrame());

            counter = goToTime;

            window.setTime((int) (counter * currentSong.getMsPerFrame()), totalTime);

            //chama a função skipToFrame
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


    /**
     * Função que faz a mistrura na lista de reporução
     */
    public void shuffleButton(){
        Thread random = new Thread(() -> {

            shuffle = !shuffle;

            /**
             * Essa parte ele vemos se tem uma música tocando
             * Porque se tiver essa música não vai fazer parte da
             * mistura, e vai ser colocada na primeira posição
             * da lista
             */
            if(isPlaying || paused) {
                if (shuffle) { //Aqui vamos fazer a mistura na lista, mas botando a música atual na posição 0

                    /**
                     * A lista shuffleList vai armazenar a lista original, para quando o
                     * o botão de shuffle for desapertado, vamos usar ela para recuperar
                     * a ordem original
                     *
                     * Então copiamos o que tem nas listas listaDeSons e listaDeMusicas para
                     * a shuffleListSong e SuffleList respectivamente
                     */
                    shuffleList.clear();
                    shuffleListSong.clear();
                    shuffleListSong.addAll(listaDeSons);
                    shuffleList.addAll(listaDeMusicas);


                    //bota a música atual na posição 0 da lista, e o que tava no 0 vai para onde tava a atual
                    shuffleSong = listaDeSons.get(actualIndex);
                    listaDeSons.set(actualIndex, listaDeSons.get(0));
                    listaDeSons.set(0, shuffleSong);

                    //Faz a mesma troca no array de Strings
                    shuffleString = listaDeMusicas.get(actualIndex);
                    listaDeMusicas.set(actualIndex, listaDeMusicas.get(0));
                    listaDeMusicas.set(0, shuffleString);

                    actualIndex = 0;

                    /**
                     * É a parte da aleatorização, basicamente o que fazemos é percorremos
                     * a lista a partir do index 1, e vamos gerando números aleatorios, e trocamos
                     * o que tinha na lista na posição desse número aleatório, com o que tem na lista
                     * na posição do index da iteração do loop
                     */
                    for (int i = 1; i < listaDeSons.size(); i++) {
                        int randomNumber = (int) Math.floor(Math.random() * (listaDeSons.size() - i) + i);
                        shuffleSong = listaDeSons.get(randomNumber);
                        listaDeSons.set(randomNumber, listaDeSons.get(i));
                        listaDeSons.set(i, shuffleSong);

                        shuffleString = listaDeMusicas.get(randomNumber);
                        listaDeMusicas.set(randomNumber, listaDeMusicas.get(i));
                        listaDeMusicas.set(i, shuffleString);
                    }

                    //atualizamos a lista na tela
                    changeQueue();

                } else { //Se entrou nesse else, é porque é para voltar a sequência original

                    /**
                     * Copiamos tudo que tinha nas listas que armazenam
                     * a sequência original para as listas principais
                     */
                    listaDeSons.clear();
                    listaDeMusicas.clear();
                    listaDeSons.addAll(shuffleListSong);
                    listaDeMusicas.addAll(shuffleList);

                    for (int i = 0; i < listaDeSons.size(); i++) {
                        if (currentSong == listaDeSons.get(i)) {
                            actualIndex = i; //Atualiza o actualIndex
                            break;
                        }
                    }
                    changeQueue(); //Atualiza a lista na tela
                }
            }

            /**
             * Se entrou nesse else é porque não tinha nenhuma música tocando
             * então vamos misturar TODAS as músicas
             */
            else{

                /**
                 * O processo é semelhante ao anterior, mas o loop agora vai percorrer a lista inteira
                 * e vai trocar todas as músicas de posição
                 */
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
