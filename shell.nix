let
 nixpkgs = import <nixpkgs> {};

in
with nixpkgs;
stdenv.mkDerivation rec {

 name = "erratic-statesman";

 buildInputs = [

  jdk
  boot

 ];

}
