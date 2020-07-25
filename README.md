# Hangman - client/server
This a client/server implementation for the game Hangman in Java that will be played through the terminal. 
The server is set up to run locally on IP: 127.0.0.1, but should be able to do it on others as well. However it is only tested locally. 
The logic of the server is build on the idea of keywords that gives a client access to do different things in terms of interacting with a game. These are covered in the [keyword documentation](#Keyword-documentation-for-hangman-game-server) below. 

The server supports multiple instances of hangman games that all will get an UUID, but as a client you can only be in one game at a time. 
When a client connects to the server for the first time, the client will get a message of all active games and how many people that are in each. 

It is possible to make a sketch of the progress in the hangman game, which can be enabled through a keyword.

Please note that the owner of the game (the one who has decided on the word to guess) cannot participate in the guessing before next round. 

Please also note that it is a good idea to start the client with a name such that it is easier for players to distinguish eachother. Otherwise they would have to do it based on IP:PORT. 
Example on starting the client from terminal:  
```sh
java Client "<NAME>" 
```

## Keyword documentation for hangman-game-server
The keywords must be written in terminal of the client and will then be send to the server. It is not importen whether or not the letters have upper- or lowercase except for when trying to join a game where a game id is sent. This must be in the right format. Note also that if a semi-colon (;) is made, then you must also do this. 

The follwing keywords are supported: 
| Keyword | Description |
| ------ | ------ |
| START_GAME;<word_to_guess> | Start a new game. When entering this a new hangman game will be created with a new UUID, and you will be set as the owner. If you are already active in another game, then you will be removed from this and transferred to the new game. The game starts, when there is 2 players in it.  |
| JOIN_GAME;<game_id> | Joins the game with the given game id (a UUID). If no such game id exists, you will be told so and nothing further happens. When entered into a game, the server will keep track of turns and notify each player on whose turn it is.|
| GUESS;<letter> | When it is your turn you can make a guess by providing a letter. The guess is registered in the game and a status on the guess is sent to you and all other players while also providing the game in the terminal. If you guess and it is not your turn, you will be told so. There is also input-validation, and provided wrong input will make you have to try again with something correct. |
| NEW_WORD;<word_to_guess | When either the guessword of the game is guessed or there is no more lives left, a new owner of the game will be found. The new owner can then write the keyword and a new word to be used in the game, which will then start with all the players already in the game. The next player in line will then be asked to perform his/her turn. |
| GAMES | This can be written at any time and will provide an overview of the active games in the server along how many people that are in it. |
| BURN | If you do not wish to take your turn you can be skipped by using the BURN keyword. You are simply burning your turn and passing it on to the next player. This has no consequence in the game. You can only burn your turn if it is actually your turn. |
| ENABLE_SKETCH | The owner of the game can use this to active a sketch of the progress of the hangman for each round. The sketch will be shown in the terminal along all the other game information and will be send to all players. Is it as default disabled, and for each new game it will also be disabled.  |
| LEAVE_GAME | If you no longer wish to be a part of the current game you are in, you can choose to leave it. This only works if it is not your turn and if you are not the owner of game. |

Further keywords exist, but they are only made for readability in code. They can be seen in src/helpers/Constants.java
