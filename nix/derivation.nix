{ system ? builtins.currentSystem
, config ? { android_sdk.accept_license = true; }, overlays ? []
, pkgs ? (import <nixpkgs> { inherit system config overlays; })
, target-os }:

let
  inherit (stdenv) isDarwin;
  inherit (stdenv.lib) catAttrs concatStrings optional unique;

  platform = pkgs.callPackage ./platform.nix { inherit target-os; };
  # Declare a specialized mkShell function which adds some bootstrapping
  #  so that e.g. STATUS_REACT_HOME is automatically available in the shell
  mkShell = (import ./bootstrapped-shell.nix { inherit stdenv; inherit (pkgs) mkShell git; });
  # TODO: Try to use stdenv for iOS. The problem is with building iOS as the build is trying to pass parameters to Apple's ld that are meant for GNU's ld (e.g. -dynamiclib)
  stdenv = pkgs.stdenvNoCC;
  maven = pkgs.maven;
  baseGo = pkgs.go_1_11;
  go = pkgs.callPackage ./patched-go { inherit baseGo; };
  buildGoPackage = pkgs.buildGoPackage.override { inherit go; };
  desktop = pkgs.callPackage ./desktop { inherit target-os stdenv status-go pkgs nodejs; inherit (pkgs) darwin; go = baseGo; };
  mobile = pkgs.callPackage ./mobile { inherit target-os config stdenv pkgs mkShell nodejs yarn status-go maven localMavenRepoBuilder mkFilter prod-build-fn; inherit (pkgs.xcodeenv) composeXcodeWrapper; };
  status-go = pkgs.callPackage ./status-go { inherit target-os go buildGoPackage; inherit (mobile.ios) xcodeWrapper; androidPkgs = mobile.android.androidComposition; };
  # mkFilter is a function that allows filtering a directory structure (used for filtering source files being captured in a closure)
  mkFilter = import ./tools/mkFilter.nix { inherit (stdenv) lib; };
  localMavenRepoBuilder = pkgs.callPackage ./tools/maven/maven-repo-builder.nix { inherit (pkgs) stdenv; };
  # Import a function that can build a prod-build target with specified node dependencies Nix expression 
  prod-build-fn = pkgs.callPackage ./targets/prod-build.nix { inherit stdenv pkgs target-os nodejs localMavenRepoBuilder mkFilter; };
  nodejs = pkgs.nodejs-10_x;
  yarn = pkgs.yarn.override { inherit nodejs; };
  selectedSources =
    optional platform.targetDesktop desktop ++
    optional platform.targetMobile mobile;

  # TARGETS
  leiningen-shell = mkShell {
    buildInputs = with pkgs; [ clojure leiningen maven nodejs ];
    shellHook =
      if target-os == "android" then mobile.android.shellHook else
      if target-os == "ios" then mobile.ios.shellHook else "";
  };
  watchman-shell = mkShell {
    buildInputs = with pkgs; [ watchman ];
  };

in {
  # CHILD DERIVATIONS
  inherit mobile;

  # TARGETS
  leiningen = {
    shell = leiningen-shell;
  };
  watchman = {
    shell = watchman-shell;
  };

  shell = {
    buildInputs = unique ([
        nodejs
        pkgs.python27 # for e.g. gyp
        yarn
      ]
      ++ optional isDarwin pkgs.cocoapods
      ++ optional (isDarwin && !platform.targetIOS) pkgs.clang
      ++ optional (!isDarwin) pkgs.gcc8
      ++ catAttrs "buildInputs" selectedSources);
    shellHook = ''
      export PATH="$STATUS_REACT_HOME/node_modules/.bin:$PATH"

      ${concatStrings (catAttrs "shellHook" selectedSources)}
    '';
  };
}
